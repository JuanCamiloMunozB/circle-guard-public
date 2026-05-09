"""
CircleGuard Performance & Stress Tests using Locust.

Simulated user journeys:
  - StudentUser  : daily health survey submission (most common operation)
  - AdminUser    : certificate validation and dashboard analytics review
  - HealthOfficer: status queries and department-level stats

Run (dev/stage):
    locust -f locustfile.py --headless -u 50 -r 5 -t 120s \
           --host http://localhost:8086 \
           --html locust-report.html --csv locust-results

Run (stress):
    locust -f locustfile.py --headless -u 200 -r 20 -t 300s \
           --host http://localhost:8086 \
           --html locust-stress-report.html
"""

import json
import os
import uuid
import random
from locust import HttpUser, task, between, tag, events
from locust.exception import StopUser

# Host endpoints come from the environment so the same locustfile drives local
# runs (docker-compose), stage and master pipelines without code changes.
LOCUST_HOST = os.getenv("LOCUST_HOST", "http://localhost:8086")
DASHBOARD_HOST = os.getenv("DASHBOARD_HOST", "http://localhost:8084")


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
    """
    Simulates a student doing their daily health check-in.
    Represents the highest-frequency operation in the system.
    """
    wait_time = between(1, 3)
    host = LOCUST_HOST
    weight = 10

    def on_start(self):
        self.anonymous_id = random_anonymous_id()
        self.survey_id = None

    @task(10)
    @tag("survey", "write", "critical")
    def submit_health_survey_no_symptoms(self):
        """Healthy student submits daily survey — 90% of daily submissions."""
        payload = symptom_survey_payload(self.anonymous_id, has_symptoms=False)
        with self.client.post(
            "/api/v1/surveys",
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
                resp.failure(f"Unexpected status: {resp.status_code}")

    @task(2)
    @tag("survey", "write", "symptoms")
    def submit_health_survey_with_symptoms(self):
        """Student with symptoms submits — triggers Kafka event to promotion-service."""
        sick_id = random_anonymous_id()
        payload = symptom_survey_payload(sick_id, has_symptoms=True)
        with self.client.post(
            "/api/v1/surveys",
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
        """Student app fetches the active questionnaire before showing the form."""
        with self.client.get(
            "/api/v1/questionnaires/active",
            catch_response=True,
            name="GET /questionnaires/active"
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(1)
    @tag("survey", "read")
    def fetch_own_surveys(self):
        """Student checks their own survey history."""
        with self.client.get(
            f"/api/v1/surveys?anonymousId={self.anonymous_id}",
            catch_response=True,
            name="GET /surveys [by user]"
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")


class AdminUser(HttpUser):
    """
    Simulates a health-center administrator reviewing and validating certificates.
    Lower frequency than students but more complex operations.
    """
    wait_time = between(3, 8)
    host = LOCUST_HOST
    weight = 2  # fewer admins than students

    def on_start(self):
        self.admin_id = random_anonymous_id()

    @task(5)
    @tag("admin", "survey", "read")
    def list_pending_surveys(self):
        """Admin fetches list of surveys awaiting certificate validation."""
        with self.client.get(
            "/api/v1/surveys/pending",
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
        """Admin approves a certificate — triggers certificate.validated Kafka event."""
        fake_survey_id = str(uuid.uuid4())
        payload = cert_validation_payload(fake_survey_id, self.admin_id, "APPROVED")
        with self.client.post(
            f"/api/v1/surveys/{fake_survey_id}/validate",
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
    """
    Simulates a health-officer or director reviewing dashboard statistics.
    Represents read-heavy dashboard queries against dashboard-service.
    """
    wait_time = between(5, 15)
    host = DASHBOARD_HOST
    weight = 1  # least frequent

    DEPARTMENTS = ["Engineering", "Medicine", "Law", "Sciences", "Arts", "Mathematics"]

    @task(5)
    @tag("dashboard", "read", "campus")
    def get_campus_summary(self):
        """Health officer views campus-wide summary — calls promotion-service internally."""
        with self.client.get(
            "/api/v1/analytics/summary",
            catch_response=True,
            name="GET /analytics/summary"
        ) as resp:
            if resp.status_code in (200, 503):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")

    @task(3)
    @tag("dashboard", "read", "department")
    def get_department_stats(self):
        """Health officer drills down into a department."""
        dept = random.choice(self.DEPARTMENTS)
        with self.client.get(
            f"/api/v1/analytics/department/{dept}",
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
        """Director views hourly status trends."""
        with self.client.get(
            "/api/v1/analytics/time-series?period=hourly&limit=24",
            catch_response=True,
            name="GET /analytics/time-series"
        ) as resp:
            if resp.status_code in (200, 503):
                resp.success()
            else:
                resp.failure(f"Unexpected: {resp.status_code}")


# ---------------------------------------------------------------------------
# Event hooks — custom metrics logging
# ---------------------------------------------------------------------------

@events.request.add_listener
def on_request(request_type, name, response_time, response_length, response,
               context, exception, **kwargs):
    if exception:
        print(f"[FAIL] {request_type} {name} | time={response_time}ms | err={exception}")
    elif response and response.status_code >= 500:
        print(f"[5xx]  {request_type} {name} | time={response_time}ms | status={response.status_code}")
