import { Plus, Trash2 } from 'lucide-react';
import { isGroup, emptyLeaf, emptyGroup, OPERATORS, type ConditionNode, type ConditionField } from '../lib/conditionTree';

interface Props {
  node: ConditionNode;
  onChange: (node: ConditionNode) => void;
  fields: ConditionField[];
  depth?: number;
}

export default function ConditionTreeEditor({ node, onChange, fields, depth = 0 }: Props) {
  if (!isGroup(node)) {
    // Ne devrait pas arriver au niveau racine (toujours un groupe), gardé pour la récursion défensive.
    return null;
  }

  const updateChild = (index: number, child: ConditionNode) => {
    const children = node.children.slice();
    children[index] = child;
    onChange({ ...node, children });
  };

  const removeChild = (index: number) => {
    onChange({ ...node, children: node.children.filter((_, i) => i !== index) });
  };

  const addLeaf = () => {
    onChange({ ...node, children: [...node.children, emptyLeaf(fields[0]?.value || '')] });
  };

  const addGroup = () => {
    onChange({ ...node, children: [...node.children, emptyGroup(fields[0]?.value || '')] });
  };

  return (
    <div className={`border border-line rounded-lg p-3 ${depth > 0 ? 'bg-surface' : 'bg-bg'}`}>
      <div className="flex items-center gap-2 mb-2">
        <span className="text-xs text-ink-soft">Combiner avec :</span>
        <div className="flex gap-1 bg-surface-2 p-0.5 rounded-md">
          {(['AND', 'OR'] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => onChange({ ...node, type: t })}
              className={`px-2.5 py-1 rounded text-xs font-medium transition-colors ${
                node.type === t ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink'
              }`}
            >
              {t === 'AND' ? 'ET' : 'OU'}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-2">
        {node.children.map((child, i) =>
          isGroup(child) ? (
            <div key={i} className="flex items-start gap-2">
              <div className="flex-1">
                <ConditionTreeEditor node={child} onChange={(c) => updateChild(i, c)} fields={fields} depth={depth + 1} />
              </div>
              <button
                type="button"
                onClick={() => removeChild(i)}
                className="p-1.5 rounded text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors mt-1"
                title="Supprimer ce groupe"
              >
                <Trash2 size={14} />
              </button>
            </div>
          ) : (
            <div key={i} className="flex items-center gap-2">
              <select
                value={child.field}
                onChange={(e) => updateChild(i, { ...child, field: e.target.value })}
                className="border border-line rounded-lg px-2 py-1.5 text-sm focus:ring-2 focus:ring-accent focus:border-accent"
              >
                {fields.map((f) => (
                  <option key={f.value} value={f.value}>{f.label}</option>
                ))}
              </select>
              <select
                value={child.operator}
                onChange={(e) => updateChild(i, { ...child, operator: e.target.value })}
                className="border border-line rounded-lg px-2 py-1.5 text-sm focus:ring-2 focus:ring-accent focus:border-accent"
              >
                {OPERATORS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
              <input
                type="text"
                value={child.value}
                onChange={(e) => updateChild(i, { ...child, value: e.target.value })}
                placeholder="Valeur"
                className="flex-1 min-w-0 border border-line rounded-lg px-2 py-1.5 text-sm focus:ring-2 focus:ring-accent focus:border-accent"
              />
              <button
                type="button"
                onClick={() => removeChild(i)}
                className="p-1.5 rounded text-ink-soft hover:text-danger hover:bg-danger/10 transition-colors shrink-0"
                title="Supprimer cette condition"
              >
                <Trash2 size={14} />
              </button>
            </div>
          )
        )}
      </div>

      <div className="flex gap-3 mt-2">
        <button
          type="button"
          onClick={addLeaf}
          className="flex items-center gap-1 text-xs font-medium text-accent hover:text-accent-strong transition-colors"
        >
          <Plus size={12} />
          Condition
        </button>
        <button
          type="button"
          onClick={addGroup}
          className="flex items-center gap-1 text-xs font-medium text-accent hover:text-accent-strong transition-colors"
        >
          <Plus size={12} />
          Groupe imbriqué
        </button>
      </div>
    </div>
  );
}
