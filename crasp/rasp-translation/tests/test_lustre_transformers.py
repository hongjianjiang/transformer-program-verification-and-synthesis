"""
Tests for Lustre Transformer
"""

import pytest

from rasptranslation.lustre.ast_nodes import (
    Application,
    BinaryOp,
    BinaryOperator,
    Equation,
    IntLiteral,
    LustreType,
    Node,
    Parameter,
    Program,
    Variable,
)
from rasptranslation.lustre.transformers import NodeRenamer
from rasptranslation.lustre.walkers import NodeNameCollector

class TestTransformer:
    def test_basic_node_renaming(self):
        """Test basic node renaming with node calls"""
        # Create a simple program with node definitions and node calls
        # node HELPER(X: bool) returns (Y: bool);
        # let
        #   Y = X;
        # tel
        #
        # node MAIN(A: bool) returns (B: bool);
        # let
        #   B = HELPER(A);
        # tel

        helper_node = Node(
            name="HELPER",
            inputs=[Parameter("X", LustreType.BOOL)],
            outputs=[Parameter("Y", LustreType.BOOL)],
            variables=[],
            equations=[Equation("Y", Variable("X"))],
            checks=[],
        )

        main_node = Node(
            name="MAIN",
            inputs=[Parameter("A", LustreType.BOOL)],
            outputs=[Parameter("B", LustreType.BOOL)],
            variables=[],
            equations=[
                Equation("B", Application("HELPER", [Variable("A")]))
            ],
            checks=[],
        )

        program = Program([helper_node, main_node])

        # Rename all nodes with a prefix
        renamed = NodeRenamer(lambda name: "node_" + name).transform_program(
            program
        )

        # Collect node names from the renamed program
        collector = NodeNameCollector()
        collector.walk_program(renamed)

        # Verify that all node names were renamed
        assert collector.defined_node_names == {"node_HELPER", "node_MAIN"}
        assert collector.used_node_names == {"node_HELPER"}

    def test_complex_node_renaming(self):
        """Test renaming with multiple node calls and nested expressions"""
        # Create a program with multiple nodes and calls
        # node ADD1(X: int) returns (Y: int);
        # let
        #   Y = X + 1;
        # tel
        #
        # node DOUBLE(X: int) returns (Y: int);
        # let
        #   Y = X + X;
        # tel
        #
        # node MAIN(N: int) returns (R: int);
        # let
        #   R = DOUBLE(ADD1(N));
        # tel

        add1_node = Node(
            name="ADD1",
            inputs=[Parameter("X", LustreType.INT)],
            outputs=[Parameter("Y", LustreType.INT)],
            variables=[],
            equations=[
                Equation(
                    "Y",
                    BinaryOp(
                        BinaryOperator.ADD, Variable("X"), IntLiteral(1)
                    ),
                )
            ],
            checks=[],
        )

        double_node = Node(
            name="DOUBLE",
            inputs=[Parameter("X", LustreType.INT)],
            outputs=[Parameter("Y", LustreType.INT)],
            variables=[],
            equations=[
                Equation(
                    "Y",
                    BinaryOp(
                        BinaryOperator.ADD, Variable("X"), Variable("X")
                    ),
                )
            ],
            checks=[],
        )

        main_node = Node(
            name="MAIN",
            inputs=[Parameter("N", LustreType.INT)],
            outputs=[Parameter("R", LustreType.INT)],
            variables=[],
            equations=[
                Equation(
                    "R",
                    Application(
                        "DOUBLE",
                        [Application("ADD1", [Variable("N")])],
                    ),
                )
            ],
            checks=[],
        )

        program = Program([add1_node, double_node, main_node])

        # Rename all nodes with uppercase prefix
        renamed = NodeRenamer(lambda name: "FN_" + name).transform_program(
            program
        )

        # Collect node names
        collector = NodeNameCollector()
        collector.walk_program(renamed)

        # Verify renaming
        assert collector.defined_node_names == {
            "FN_ADD1",
            "FN_DOUBLE",
            "FN_MAIN",
        }
        assert collector.used_node_names == {"FN_ADD1", "FN_DOUBLE"}

    def test_node_not_called(self):
        """Test that unused nodes are still renamed correctly"""
        # node UNUSED(X: bool) returns (Y: bool);
        # let
        #   Y = X;
        # tel
        #
        # node MAIN(A: bool) returns (B: bool);
        # let
        #   B = A;
        # tel

        unused_node = Node(
            name="UNUSED",
            inputs=[Parameter("X", LustreType.BOOL)],
            outputs=[Parameter("Y", LustreType.BOOL)],
            variables=[],
            equations=[Equation("Y", Variable("X"))],
            checks=[],
        )

        main_node = Node(
            name="MAIN",
            inputs=[Parameter("A", LustreType.BOOL)],
            outputs=[Parameter("B", LustreType.BOOL)],
            variables=[],
            equations=[Equation("B", Variable("A"))],
            checks=[],
        )

        program = Program([unused_node, main_node])

        # Rename all nodes
        renamed = NodeRenamer(lambda name: "renamed_" + name).transform_program(
            program
        )

        # Collect node names
        collector = NodeNameCollector()
        collector.walk_program(renamed)

        # Both nodes should be defined with new names
        assert collector.defined_node_names == {
            "renamed_UNUSED",
            "renamed_MAIN",
        }
        # But no nodes are used
        assert collector.used_node_names == set()


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
