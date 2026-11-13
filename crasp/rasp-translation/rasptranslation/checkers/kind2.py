
import subprocess
import xml.etree.ElementTree as ET

from dataclasses import dataclass

from rasptranslation.lustre.ast_nodes import Program
from rasptranslation.lustre.printer import print_lustre

from typing import List, Optional

@dataclass
class Kind2Config:
    executable : str = "kind2"

    @staticmethod
    def load(toml) -> 'Kind2Config':
        """Load from a toml file from tomllib.load"""
        return Kind2Config(
            executable = toml["kind2_executable"]
        )

@dataclass
class Kind2Result:
    counter_example : Optional[List[int]] = None

class Kind2:
    def __init__(self, config : Kind2Config):
        self.config = config
        
    def get_stream(self, lustre: Program, cex_node: str, stream_name: str):
        kind_run = subprocess.run(
            [self.config.executable, "-xml"],
            input=print_lustre(lustre),
            capture_output=True,
            encoding="utf8",
        )
        result = ET.fromstring(kind_run.stdout)
        return self._get_counter_example(result, cex_node, stream_name)

    def _parse_value_text(self, s: str):
        s = s.strip().lower()
        if s == "true":
            return True
        if s == "false":
            return False
        return int(s)
    
    def _get_counter_example(
        self,
        result,
        cex_node: str,
        cex_stream: str,
    ):
        cex = result.find(
            ".//CounterExample"
            f"/Node[@name='{cex_node}']"
            f"/Stream[@name='{cex_stream}']",
        )
        if cex is not None:
            values = [
                v
                for _, v in sorted(
                    (int(i.get("instant")), self._parse_value_text(i.text))
                    for i in cex.findall("Value")
                )
            ]
            return values
        else:
            return None


    def run(
        self,
        lustre : Program,
        cex_node : str,
        cex_stream : str,
    ) -> Kind2Result:
        """Run Kind2
        Expected that there is only one check command in the program, so
        counter examples / results can be identified.
        :param lustre: the input Lustre
        :param cex_node: look for counter examples in this node
        :param cex_stream: the variable name that constitutes the
        counter example"""

        kind_run = subprocess.run(
            [self.config.executable, "-xml"],
            input = print_lustre(lustre),
            capture_output = True,
            encoding = "utf8"
        )

        result = ET.fromstring(kind_run.stdout)

        return Kind2Result(
            counter_example = self._get_counter_example(
                result,
                cex_node,
                cex_stream,
            )
        )

    def _get_counter_example(
        self,
        result,
        cex_node: str,
        cex_stream: str,
    ):
        """
        Return the stream trace from the FIRST CounterExample (if any).
        If there is no CounterExample, or stream not present, return None.
        """

        # Find the first counterexample node (Kind2 may output multiple)
        cex_root = result.find(".//CounterExample")
        if cex_root is None:
            return None

        stream_elem = cex_root.find(
            f"./Node[@name='{cex_node}']/Stream[@name='{cex_stream}']"
        )
        if stream_elem is None:
            return None

        # Parse and sort by instant
        items = []
        for v in stream_elem.findall("./Value"):
            inst = v.get("instant")
            if inst is None or v.text is None:
                continue
            items.append((int(inst), self._parse_value_text(v.text)))

        items.sort(key=lambda t: t[0])
        return [val for _, val in items]


