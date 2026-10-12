"""Upload a bundle built by central-bundle.py to Maven Central and wait until Central has validated it.

The upload uses the Central Publisher API with publishing type USER_MANAGED: a valid bundle stops in the state
VALIDATED, and a maintainer publishes or drops it on central.sonatype.com (Publish -> Deployments). Nothing is
published by this script.

Credentials: a Central user token (username and password generated on central.sonatype.com, not the account login),
read from the environment variables CENTRAL_TOKEN_USERNAME and CENTRAL_TOKEN_PASSWORD and sent as
"Authorization: Bearer base64(username:password)".

Usage: python scripts/central-upload.py <bundle.zip> <deployment-name>
Exit code 0 when the deployment is VALIDATED, 1 when Central rejects it or does not finish in time, 2 on bad usage.
"""
import base64
import enum
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

API = "https://central.sonatype.com/api/v1/publisher"
USERNAME_VARIABLE = "CENTRAL_TOKEN_USERNAME"
PASSWORD_VARIABLE = "CENTRAL_TOKEN_PASSWORD"
POLL_INTERVAL_SECONDS = 10
TIMEOUT_SECONDS = 30 * 60


class DeploymentState(enum.Enum):
    """States of a Central deployment, as the status endpoint reports them."""
    PENDING = "PENDING"
    VALIDATING = "VALIDATING"
    VALIDATED = "VALIDATED"
    PUBLISHING = "PUBLISHING"
    PUBLISHED = "PUBLISHED"
    FAILED = "FAILED"

    @property
    def is_in_progress(self) -> bool:
        return self in (DeploymentState.PENDING, DeploymentState.VALIDATING)


def authorization() -> str:
    """The Authorization header value built from the user token in the environment."""
    token = f"{os.environ[USERNAME_VARIABLE]}:{os.environ[PASSWORD_VARIABLE]}"
    return "Bearer " + base64.b64encode(token.encode()).decode()


def upload(bundle: Path, name: str) -> str:
    """Uploads the bundle as one multipart/form-data field `bundle`; returns the deployment id."""
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="bundle"; filename="{bundle.name}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n").encode() \
        + bundle.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    query = urllib.parse.urlencode({"name": name, "publishingType": "USER_MANAGED"})
    request = urllib.request.Request(f"{API}/upload?{query}", data=body, method="POST", headers={
        "Authorization": authorization(),
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    })
    with urllib.request.urlopen(request) as response:
        return response.read().decode().strip()


def status(deployment_id: str) -> dict:
    """The status document of a deployment (deploymentState, errors, ...)."""
    query = urllib.parse.urlencode({"id": deployment_id})
    request = urllib.request.Request(f"{API}/status?{query}", data=b"", method="POST",
                                     headers={"Authorization": authorization()})
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def wait_for_validation(deployment_id: str) -> tuple[DeploymentState, dict]:
    """Polls until the deployment leaves PENDING / VALIDATING or the timeout passes; returns the last state."""
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while True:
        document = status(deployment_id)
        state = DeploymentState(document["deploymentState"])
        if not state.is_in_progress or time.monotonic() > deadline:
            return state, document
        print(f"deployment {deployment_id}: {state.value}", flush=True)
        time.sleep(POLL_INTERVAL_SECONDS)


def main(args: list[str]) -> int:
    if len(args) != 2:
        print(__doc__)
        return 2
    missing_variables = [v for v in (USERNAME_VARIABLE, PASSWORD_VARIABLE) if not os.environ.get(v)]
    if missing_variables:
        print("not set: " + ", ".join(missing_variables))
        return 2
    bundle, name = Path(args[0]), args[1]
    deployment_id = upload(bundle, name)
    print(f"uploaded {bundle} as deployment {deployment_id} ({name})", flush=True)
    state, document = wait_for_validation(deployment_id)
    print(f"deployment {deployment_id}: {state.value}")
    if state is DeploymentState.VALIDATED:
        print("publish or drop it on https://central.sonatype.com/publishing/deployments")
        return 0
    if document.get("errors"):
        print(json.dumps(document["errors"], indent=2))
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
