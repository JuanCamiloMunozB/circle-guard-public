"""
CircleGuard Performance & Stress Tests using Locust.

Simulated user journeys:
  - StudentUser  : daily health survey submission (most common operation)
  - AdminUser    : certificate validation and dashboard analytics review
  - HealthOfficer: status queries and department-level stats

Each request uses an ABSOLUTE URL built from a per-service env var (LOCUST_HOST,
GATEWAY_HOST, FILE_HOST, DASHBOARD_HOST). This is deliberate: in headless mode with
several User classes that each target a different service, Locust applies a single
host to every user, so relying on the per-class `host` attribute would send all
traffic to one service (causing 404s on the others). Absolute URLs avoid that.

Run (dev/stage):
    LOCUST_HOST=http://form-service:8086 GATEWAY_HOST=http://gateway-service:8087 \
    FILE_HOST=http://file-service:8085 DASHBOARD_HOST=http://dashboard-service:8084 \
    locust -f locustfile.py --headless -u 10 -r 2 -t 60s
"""

import os
import uuid
import random
from locust import HttpUser, task, between, tag, events

# Per-service base URLs from the environment so the same locustfile drives local
# runs, stage and master without code changes.
LOCUST_HOST       = os.getenv("LOCUST_HOST",       "http://localhost:8086")   # form-service
DASHBOARD_HOST    = os.getenv("DASHBOARD_HOST",    "http://localhost:8084")   # dashboard-service
GATEWAY_HOST      = os.getenv("GATEWAY_HOST",      "http://localhost:8087")   # gateway-service
FILE_HOST         = os.getenv("FILE_HOST",         "http://localhost:8085")   # file-service
NOTIFICATION_HOST = os.getenv("NOTIFICATION_HOST", "http://localhost:8082")   # notification-service


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def random_anonymous_id() -> str:
    return str(uuid.uuid4())


def symptom_survey_payload(anonymous_id: str, has_symptoms: bool = False) -> dict:
    return {
        "anonymousId": anonymous_id,
        "hasFever": has_symptoms,
        "hasCough": has_symptoms,
        "responses": {
            str(uuid.uuid4()): "YES" if has_symptoms else "NO"
        }
    }


def cert_validation_payload(survey_id: str, admin_id: str, status: str = "APPROVED") -> dict:
    return {
        "status": status,
        "adminId": admin_id
    }


# ---------------------------------------------------------------------------
# User classes
# ---------------------------------------------------------------------------

class StudentUser(HttpUser):
    """Simulates a student doing their daily health check-in (highest frequency)."""
    wait_time = between(1, 3)
    host = LOCUST_HOST
    weight = 10

    def on_start(self):
        self.anonymous_id = random_anonymous_id()
        self.survey_id = None

    @task(10)
    @tag("survey", "write", "critical")
    def submit_health_survey_no_symptoms(self):
        payload = symptom_survey_payload(self.anonymous_id, has_symptoms=False)
        with self.client.post(
            f"{LOCUST_HOST}/api/v1/surveys",
            json=payload,
            catch_response=True,
            name="POST /surveys [healthy]"
        ) as resp:
            if resp.status_code == 200:
                data = resp.json()
                self.survey_id = data.get("id")
                resp.success()
            elif resp.status_code in (503, 504):
                resp.failure(f"Service unavailable: {resp.status_code}")
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(2)
    @tag("survey", "write", "symptoms")
    def submit_health_survey_with_symptoms(self):
        sick_id = random_anonymous_id()
        payload = symptom_survey_payload(sick_id, has_symptoms=True)
        with self.client.post(
            f"{LOCUST_HOST}/api/v1/surveys",
            json=payload,
            catch_response=True,
            name="POST /surveys [symptoms]"
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            elif resp.status_code in (503, 504):
                resp.failure(f"Service unavailable: {resp.status_code}")
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(3)
    @tag("questionnaire", "read")
    def fetch_active_questionnaire(self):
        with self.client.get(
            f"{LOCUST_HOST}/api/v1/questionnaires/active",
            catch_response=True,
            name="GET /questionnaires/active"
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(1)
    @tag("questionnaire", "read")
    def list_questionnaires(self):
        with self.client.get(
            f"{LOCUST_HOST}/api/v1/questionnaires",
            catch_response=True,
            name="GET /questionnaires [list]"
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")


class AdminUser(HttpUser):
    """Simulates a health-center administrator reviewing and validating certificates."""
    wait_time = between(3, 8)
    host = LOCUST_HOST
    weight = 2

    def on_start(self):
        self.admin_id = random_anonymous_id()

    @task(5)
    @tag("admin", "survey", "read")
    def list_pending_surveys(self):
        with self.client.get(
            f"{LOCUST_HOST}/api/v1/surveys/pending",
            catch_response=True,
            name="GET /surveys/pending"
        ) as resp:
            if resp.status_code in (200, 401, 403, 404):
                resp.success()
            elif resp.status_code in (503, 504):
                resp.failure(f"Service unavailable: {resp.status_code}")
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(3)
    @tag("admin", "survey", "write")
    def validate_certificate(self):
        fake_survey_id = str(uuid.uuid4())
        payload = cert_validation_payload(fake_survey_id, self.admin_id, "APPROVED")
        with self.client.post(
            f"{LOCUST_HOST}/api/v1/surveys/{fake_survey_id}/validate",
            json=payload,
            catch_response=True,
            name="POST /surveys/{id}/validate"
        ) as resp:
            # 404 is expected since fake_survey_id won't exist in a real env
            if resp.status_code in (200, 404, 401, 403):
                resp.success()
            elif resp.status_code in (503, 504):
                resp.failure(f"Service unavailable: {resp.status_code}")
            else:
                resp.failure(f"Unexpected: {resp.status_code}")


class DashboardUser(HttpUser):
    """Simulates a health-officer reviewing dashboard statistics (read-heavy)."""
    wait_time = between(5, 15)
    host = DASHBOARD_HOST
    weight = 1

    DEPARTMENTS = ["Engineering", "Medicine", "Law", "Sciences", "Arts", "Mathematics"]

    @task(5)
    @tag("dashboard", "read", "campus")
    def get_campus_summary(self):
        with self.client.get(
            f"{DASHBOARD_HOST}/api/v1/analytics/summary",
            catch_response=True,
            name="GET /analytics/summary"
        ) as resp:
            if resp.status_code in (200, 404, 503):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(3)
    @tag("dashboard", "read", "department")
    def get_department_stats(self):
        dept = random.choice(self.DEPARTMENTS)
        with self.client.get(
            f"{DASHBOARD_HOST}/api/v1/analytics/department/{dept}",
            catch_response=True,
            name="GET /analytics/department/{dept}"
        ) as resp:
            if resp.status_code in (200, 404, 503):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(2)
    @tag("dashboard", "read", "timeseries")
    def get_time_series(self):
        with self.client.get(
            f"{DASHBOARD_HOST}/api/v1/analytics/time-series?period=hourly&limit=24",
            catch_response=True,
            name="GET /analytics/time-series"
        ) as resp:
            if resp.status_code in (200, 404, 503):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")


class GatewayUser(HttpUser):
    """Simulates campus-entry QR-code scans hitting gateway-service."""
    wait_time = between(1, 5)
    host = GATEWAY_HOST
    weight = 8

    def on_start(self):
        self.anonymous_id = random_anonymous_id()
        self.token = None

    @task(10)
    @tag("gateway", "qr", "read", "critical")
    def validate_qr_token(self):
        payload = {"token": self.token or "dummy-token-for-load-test"}
        with self.client.post(
            f"{GATEWAY_HOST}/api/v1/gate/validate",
            json=payload,
            catch_response=True,
            name="POST /gate/validate"
        ) as resp:
            if resp.status_code in (200, 400, 401, 404):
                resp.success()
            elif resp.status_code in (503, 504):
                resp.failure(f"Service unavailable: {resp.status_code}")
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(2)
    @tag("gateway", "health")
    def check_actuator_health(self):
        with self.client.get(
            f"{GATEWAY_HOST}/actuator/health",
            catch_response=True,
            name="GET /actuator/health [gateway]"
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Health probe failed: {resp.status_code}")


class FileUser(HttpUser):
    """Simulates staff uploading certificates and downloading files via file-service."""
    wait_time = between(5, 20)
    host = FILE_HOST
    weight = 3

    SAMPLE_PDF = b"%PDF-1.4 1 0 obj<</Type/Catalog>>endobj"

    @task(5)
    @tag("file", "upload", "write")
    def upload_certificate(self):
        with self.client.post(
            f"{FILE_HOST}/api/v1/files/upload",
            files={"file": ("certificate.pdf", self.SAMPLE_PDF, "application/pdf")},
            catch_response=True,
            name="POST /files/upload"
        ) as resp:
            if resp.status_code in (200, 201, 202):
                resp.success()
            elif resp.status_code in (400, 422):
                resp.success()  # service is up and rejecting correctly
            elif resp.status_code in (503, 504):
                resp.failure(f"Service unavailable: {resp.status_code}")
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(3)
    @tag("file", "download", "read")
    def get_file_metadata(self):
        fake_id = str(uuid.uuid4())
        with self.client.get(
            f"{FILE_HOST}/api/v1/files/{fake_id}",
            catch_response=True,
            name="GET /files/{id}"
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()
            elif resp.status_code in (503, 504):
                resp.failure(f"Service unavailable: {resp.status_code}")
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(1)
    @tag("file", "health")
    def check_actuator_health(self):
        with self.client.get(
            f"{FILE_HOST}/actuator/health",
            catch_response=True,
            name="GET /actuator/health [file]"
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Health probe failed: {resp.status_code}")


# ---------------------------------------------------------------------------
# Event hooks — custom metrics logging
# ---------------------------------------------------------------------------

@events.request.add_listener
def on_request(request_type, name, response_time, response_length, response,
               context, exception, **kwargs):
    if exception:
        print(f"[FAIL] {request_type} {name} | time={response_time}ms | err={exception}")
    elif response is not None and response.status_code >= 500:
        print(f"[5xx]  {request_type} {name} | time={response_time}ms | status={response.status_code}")
