"""Private credential input for a separately approved Atlas inventory run."""
import getpass
import pathlib
import subprocess
import sys
import warnings

if __name__ == "__main__":
    try:
        if len(sys.argv) != 3 or not sys.stdin.isatty():
            raise ValueError()
        with warnings.catch_warnings():
            warnings.simplefilter("error", getpass.GetPassWarning)
            password = getpass.getpass("Temporary inventory password (hidden): ")
        if not password or len(password.encode()) > 1024:
            raise ValueError()
        result = subprocess.run(
            ["node", str(pathlib.Path(__file__).with_name("atlas-inventory-cli.cjs")),
             sys.argv[1], sys.argv[2]],
            input=password.encode(), check=False,
        )
        password = ""
        sys.exit(result.returncode)
    except (Exception, KeyboardInterrupt):
        print("Private inventory launch refused.", file=sys.stderr)
        sys.exit(1)
