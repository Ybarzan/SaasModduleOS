import { describe, it, expect } from "vitest";
import {
  describeCondition, hasIncompleteLeaf, emptyGroup, emptyLeaf, isGroup,
  type ConditionGroup,
} from "../lib/conditionTree";

const FIELDS = [
  { value: "newStatus", label: "Nouveau statut" },
  { value: "dataSource", label: "Provenance" },
];

describe("conditionTree helpers", () => {
  it("emptyGroup creates an AND group with one empty leaf using the given default field", () => {
    const g = emptyGroup("newStatus");
    expect(g.type).toBe("AND");
    expect(g.children).toHaveLength(1);
    expect(g.children[0]).toEqual({ type: "LEAF", field: "newStatus", operator: "EQ", value: "" });
  });

  it("isGroup distinguishes AND/OR groups from leaves", () => {
    expect(isGroup(emptyGroup("x"))).toBe(true);
    expect(isGroup(emptyLeaf("x"))).toBe(false);
  });

  it("describeCondition renders a single leaf without parentheses", () => {
    const g: ConditionGroup = { type: "AND", children: [{ type: "LEAF", field: "newStatus", operator: "EQ", value: "BOOKED" }] };
    expect(describeCondition(g, FIELDS)).toBe('Nouveau statut est égal à "BOOKED"');
  });

  it("describeCondition joins multiple leaves with the group's connector and wraps in parentheses", () => {
    const g: ConditionGroup = {
      type: "OR",
      children: [
        { type: "LEAF", field: "newStatus", operator: "EQ", value: "BOOKED" },
        { type: "LEAF", field: "dataSource", operator: "NEQ", value: "LIVE" },
      ],
    };
    expect(describeCondition(g, FIELDS)).toBe(
      '(Nouveau statut est égal à "BOOKED" OU Provenance est différent de "LIVE")'
    );
  });

  it("describeCondition recurses into nested groups", () => {
    const g: ConditionGroup = {
      type: "AND",
      children: [
        { type: "LEAF", field: "newStatus", operator: "EQ", value: "BOOKED" },
        { type: "OR", children: [{ type: "LEAF", field: "dataSource", operator: "EQ", value: "LIVE" }] },
      ],
    };
    expect(describeCondition(g, FIELDS)).toBe(
      '(Nouveau statut est égal à "BOOKED" ET Provenance est égal à "LIVE")'
    );
  });

  it("describeCondition falls back to the raw field/operator value when unknown", () => {
    const g: ConditionGroup = { type: "AND", children: [{ type: "LEAF", field: "mystery", operator: "EQ", value: "x" }] };
    expect(describeCondition(g, FIELDS)).toBe('mystery est égal à "x"');
  });

  it("hasIncompleteLeaf is true for an empty group", () => {
    expect(hasIncompleteLeaf({ type: "AND", children: [] })).toBe(true);
  });

  it("hasIncompleteLeaf is true when a leaf's value is blank", () => {
    const g: ConditionGroup = { type: "AND", children: [{ type: "LEAF", field: "newStatus", operator: "EQ", value: "  " }] };
    expect(hasIncompleteLeaf(g)).toBe(true);
  });

  it("hasIncompleteLeaf is true when a nested group contains an incomplete leaf", () => {
    const g: ConditionGroup = {
      type: "AND",
      children: [
        { type: "LEAF", field: "newStatus", operator: "EQ", value: "BOOKED" },
        { type: "OR", children: [{ type: "LEAF", field: "dataSource", operator: "EQ", value: "" }] },
      ],
    };
    expect(hasIncompleteLeaf(g)).toBe(true);
  });

  it("hasIncompleteLeaf is false when every leaf is fully filled", () => {
    const g: ConditionGroup = {
      type: "AND",
      children: [
        { type: "LEAF", field: "newStatus", operator: "EQ", value: "BOOKED" },
        { type: "OR", children: [{ type: "LEAF", field: "dataSource", operator: "EQ", value: "LIVE" }] },
      ],
    };
    expect(hasIncompleteLeaf(g)).toBe(false);
  });
});
