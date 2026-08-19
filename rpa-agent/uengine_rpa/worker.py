"""Headless server-side RPA worker (Docker entrypoint).

    python -m uengine_rpa.worker

Environment:
    UENGINE_BASE_URL      process-service URL (default http://localhost:9094)
    UENGINE_AGENT_ID      optional stable agent id
    UENGINE_POLL_INTERVAL poll interval seconds (default 3)
"""

import os

from .runner import RpaRunner, DEFAULT_BASE_URL


def main():
    runner = RpaRunner(
        base_url=os.environ.get("UENGINE_BASE_URL", DEFAULT_BASE_URL),
        mode="server",
        agent_id=os.environ.get("UENGINE_AGENT_ID"),
    )
    runner.run_forever()


if __name__ == "__main__":
    main()
