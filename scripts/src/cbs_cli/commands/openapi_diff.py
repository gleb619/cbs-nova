import argparse
import sys

from ..openapi.classify import classify, load_spec


class OpenApiDiffCommand:
    def run(self, args: argparse.Namespace) -> int:
        old = load_spec(args.old_path)
        new = load_spec(args.new_path)

        breaking, additive = classify(old, new)

        old_v = (old.get("info") or {}).get("version")
        new_v = (new.get("info") or {}).get("version")

        print(f"info.version: {old_v!r} -> {new_v!r}")
        if breaking:
            print(f"BREAKING ({len(breaking)}):")
            for line in breaking:
                print(f"  - {line}")
        if additive:
            print(f"ADDITIVE ({len(additive)}):")
            for line in additive:
                print(f"  - {line}")
        if not breaking and not additive:
            print("[ok] no schema-shape changes")

        if not breaking:
            return 0
        if old_v != new_v:
            print("[warn] breaking changes but info.version bumped")
            return 0
        return 2
