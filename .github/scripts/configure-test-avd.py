"""Set actual AVD RAM/VM heap keys (the runner's heap-size sets hw.heapSize)."""
from pathlib import Path
import sys

config = Path(sys.argv[1])
values = {"hw.ramSize": "2048", "vm.heapSize": "256"}
lines = [line for line in config.read_text().splitlines()
         if line.split("=", 1)[0].strip() not in values]
config.write_text("\n".join(lines + [f"{key}={value}" for key, value in values.items()]) + "\n")
