import { Modal } from 'flowbite-react';
import { useGetRcdoTreeQuery, type RcdoNode } from '../store/api';

const TYPE_LABEL: Record<RcdoNode['nodeType'], string> = {
  RALLY_CRY: 'Rally Cry',
  DEFINING_OBJECTIVE: 'Defining Objective',
  OUTCOME: 'Outcome',
  SUPPORTING_OUTCOME: 'Supporting Outcome',
};

interface RcdoPickerProps {
  open: boolean;
  onClose: () => void;
  /** Called with the chosen leaf when a Supporting Outcome is selected. */
  onSelect: (node: RcdoNode) => void;
}

/**
 * A single tree node. Every level renders for navigation/ancestry context, but
 * only SUPPORTING_OUTCOME (leaf) nodes are selectable — clicking one calls the
 * picker's `onSelect`/`onClose`. Non-leaf nodes are display-only.
 */
function PickerNode({
  node,
  onPick,
}: {
  node: RcdoNode;
  onPick: (node: RcdoNode) => void;
}) {
  const selectable = node.nodeType === 'SUPPORTING_OUTCOME';
  const label = (
    <>
      <span className="font-medium">{node.title}</span>{' '}
      <span className="text-xs uppercase tracking-wide text-gray-400">
        {TYPE_LABEL[node.nodeType]}
      </span>
    </>
  );

  return (
    <li className="mt-1">
      {selectable ? (
        <button
          type="button"
          onClick={() => onPick(node)}
          className="rounded px-2 py-1 text-left text-blue-700 hover:bg-blue-50 hover:underline"
        >
          {label}
        </button>
      ) : (
        <span className="text-gray-700">{label}</span>
      )}
      {node.children.length > 0 && (
        <ul className="ml-4 border-l border-gray-200 pl-4">
          {node.children.map((child) => (
            <PickerNode key={child.id} node={child} onPick={onPick} />
          ))}
        </ul>
      )}
    </li>
  );
}

/**
 * Modal picker over B's read-only RCDO tree data. Navigable across all hierarchy
 * levels for ancestry context, but only SUPPORTING_OUTCOME leaves are selectable
 * — this is the interaction that makes the RCDO link unskippable (UX-R4). The
 * selected node carries the full RcdoNode so the caller can show the Outcome
 * title + ancestry (UX-R5). Reuses the existing getRcdoTree query (no new query).
 */
export default function RcdoPicker({ open, onClose, onSelect }: RcdoPickerProps) {
  const { data, isLoading, isError } = useGetRcdoTreeQuery(undefined, {
    skip: !open,
  });

  function handlePick(node: RcdoNode) {
    onSelect(node);
    onClose();
  }

  let body;
  if (isLoading) {
    body = <p className="text-gray-500">Loading strategy hierarchy…</p>;
  } else if (isError) {
    body = (
      <p role="alert" className="text-red-600">
        Strategy hierarchy unavailable
      </p>
    );
  } else if (!data || data.length === 0) {
    body = <p className="text-gray-500">No strategy nodes yet</p>;
  } else {
    body = (
      <ul className="text-left">
        {data.map((root) => (
          <PickerNode key={root.id} node={root} onPick={handlePick} />
        ))}
      </ul>
    );
  }

  return (
    <Modal show={open} onClose={onClose} dismissible>
      <Modal.Header>Pick a Supporting Outcome</Modal.Header>
      <Modal.Body>{body}</Modal.Body>
    </Modal>
  );
}
