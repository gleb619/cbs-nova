#!/usr/bin/env python3
import os
import subprocess
import sys

here = os.path.dirname(os.path.abspath(__file__))
sys.exit(subprocess.run([sys.executable, os.path.join(here, "cbs_cli.py"), "openapi-diff"] + sys.argv[1:]).returncode)
