import argparse
import json
import sys

from ..config import Config
from ..curl import Curl
from ..docker import Docker
from ..printer import Printer


class DoctorCommand:
    def run(self, args: argparse.Namespace) -> int:
        Printer.header("Running smoke checks...")
        print()
        fails = 0

        if Docker.is_stack_running():
            Printer.ok("Docker compose stack has running services")
        else:
            Printer.fail("Docker compose stack has running services")
            fails += 1

        if Curl._base("http://localhost:8080/realms/master", timeout=5, fail=False).returncode == 0:
            Printer.ok("Keycloak (http://localhost:8080/realms/master)")
        else:
            Printer.fail("Keycloak (http://localhost:8080/realms/master)")
            fails += 1

        if Curl._base("http://localhost:8000/", timeout=5, fail=False).returncode == 0:
            Printer.ok("Bugsink (http://localhost:8000/)")
        else:
            Printer.fail("Bugsink (http://localhost:8000/)")
            fails += 1

        body = Curl.get(
            f"{Config.BACKEND_BASE_URL}/actuator/health", timeout=5, fail=False
        )
        if '"status":"UP"' in body:
            Printer.ok(f"Backend actuator health ({Config.BACKEND_BASE_URL}/actuator/health)")
        else:
            Printer.fail(f"Backend actuator health ({Config.BACKEND_BASE_URL}/actuator/health)")
            fails += 1

        if "temporal" in Docker.services():
            if Curl._base("http://localhost:8233/", timeout=5, fail=False).returncode == 0:
                Printer.ok("Temporal UI (http://localhost:8233/)")
            else:
                Printer.fail("Temporal UI (http://localhost:8233/)")
                fails += 1
        else:
            Printer.skip("Temporal service not declared in compose — skipping Temporal health check")

        if Curl._base(Config.BFF_BASE_URL, timeout=5, fail=False).returncode == 0:
            Printer.ok(f"BFF reachable ({Config.BFF_BASE_URL})")
        else:
            Printer.fail(f"BFF reachable ({Config.BFF_BASE_URL})")
            fails += 1

        helpers_body = Curl.get(
            f"{Config.BFF_BASE_URL}/api/v1/dsl/helpers", timeout=5, fail=False
        )
        if helpers_body and "[" in helpers_body:
            try:
                data = json.loads(helpers_body)
                if isinstance(data, list) and len(data) > 0:
                    Printer.ok(f"DSL helpers catalog non-empty ({Config.BFF_BASE_URL}/api/v1/dsl/helpers)")
                else:
                    Printer.fail(f"DSL helpers catalog empty ({Config.BFF_BASE_URL}/api/v1/dsl/helpers)")
                    fails += 1
            except json.JSONDecodeError:
                Printer.ok(f"DSL helpers catalog non-empty ({Config.BFF_BASE_URL}/api/v1/dsl/helpers)")
        else:
            Printer.fail(f"DSL helpers catalog unreachable or invalid ({Config.BFF_BASE_URL}/api/v1/dsl/helpers)")
            fails += 1

        code = Curl.status_code(f"{Config.BFF_BASE_URL}/api/v1/dsl/definitions", timeout=5)
        if code == "200":
            Printer.ok(f"DSL definitions list ({Config.BFF_BASE_URL}/api/v1/dsl/definitions)")
        else:
            Printer.fail(f"DSL definitions list ({Config.BFF_BASE_URL}/api/v1/dsl/definitions)")
            fails += 1

        if fails:
            print(f"\n{fails} check(s) failed.")
            return 1
        print("\nAll required checks passed.")
        return 0
