from .colors import Colors


class Printer:
    @staticmethod
    def ok(msg: str) -> None:
        print(f"    {Colors.GREEN}[ok]{Colors.RESET}   {msg}")

    @staticmethod
    def fail(msg: str) -> None:
        print(f"    {Colors.RED}[fail]{Colors.RESET} {msg}")

    @staticmethod
    def skip(msg: str) -> None:
        print(f"    {Colors.YELLOW}[skip]{Colors.RESET} {msg}")

    @staticmethod
    def warn(msg: str) -> None:
        print(f"    {Colors.YELLOW}[warn]{Colors.RESET} {msg}")

    @staticmethod
    def header(msg: str) -> None:
        print(f"\n==> {msg}")
