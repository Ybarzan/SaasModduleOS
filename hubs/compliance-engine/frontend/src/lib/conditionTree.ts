export interface ConditionLeaf {
  type: 'LEAF';
  field: string;
  operator: string;
  value: string;
}

export interface ConditionGroup {
  type: 'AND' | 'OR';
  children: ConditionNode[];
}

export type ConditionNode = ConditionLeaf | ConditionGroup;

export interface ConditionField {
  value: string;
  label: string;
}

export const OPERATORS = [
  { value: 'EQ', label: 'est égal à' },
  { value: 'NEQ', label: 'est différent de' },
  { value: 'CONTAINS', label: 'contient' },
  { value: 'IN', label: 'est parmi (valeurs séparées par virgules)' },
  { value: 'GT', label: 'est supérieur à' },
  { value: 'GTE', label: 'est supérieur ou égal à' },
  { value: 'LT', label: 'est inférieur à' },
  { value: 'LTE', label: 'est inférieur ou égal à' },
];

export function isGroup(node: ConditionNode): node is ConditionGroup {
  return node.type === 'AND' || node.type === 'OR';
}

export function emptyLeaf(defaultField: string): ConditionLeaf {
  return { type: 'LEAF', field: defaultField, operator: 'EQ', value: '' };
}

export function emptyGroup(defaultField: string): ConditionGroup {
  return { type: 'AND', children: [emptyLeaf(defaultField)] };
}

/** Description compacte lisible par un humain, utilisée sur la carte de règle. */
export function describeCondition(node: ConditionNode, fields: ConditionField[]): string {
  if (isGroup(node)) {
    const connector = node.type === 'AND' ? ' ET ' : ' OU ';
    const parts = node.children.map((c) => describeCondition(c, fields));
    return node.children.length > 1 ? `(${parts.join(connector)})` : parts.join(connector) || '(vide)';
  }
  const fieldLabel = fields.find((f) => f.value === node.field)?.label || node.field;
  const opLabel = OPERATORS.find((o) => o.value === node.operator)?.label || node.operator;
  return `${fieldLabel} ${opLabel} "${node.value}"`;
}

/** Une condition avec un champ/valeur vide n'a pas de sens à envoyer — validation légère
 * côté client, la validation structurelle qui fait foi reste côté backend (RuleConditionEvaluator). */
export function hasIncompleteLeaf(node: ConditionNode): boolean {
  if (isGroup(node)) return node.children.length === 0 || node.children.some(hasIncompleteLeaf);
  return !node.field || !node.operator || !node.value.trim();
}
