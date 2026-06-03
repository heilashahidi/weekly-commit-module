import { useGetRcdoTreeQuery, type RcdoNode } from '../store/api';

const TYPE_LABEL: Record<RcdoNode['nodeType'], string> = {
  RALLY_CRY: 'Rally Cry',
  DEFINING_OBJECTIVE: 'Defining Objective',
  OUTCOME: 'Outcome',
  SUPPORTING_OUTCOME: 'Supporting Outcome',
};

function TreeNode({ node }: { node: RcdoNode }) {
  return (
    <li className="mt-1">
      <span className="text-gray-700">
        <span className="font-medium">{node.title}</span>{' '}
        <span className="text-xs uppercase tracking-wide text-gray-400">
          {TYPE_LABEL[node.nodeType]}
        </span>
      </span>
      {node.children.length > 0 && (
        <ul className="ml-4 border-l border-gray-200 pl-4">
          {node.children.map((child) => (
            <TreeNode key={child.id} node={child} />
          ))}
        </ul>
      )}
    </li>
  );
}

/**
 * Read-only viewer for the RCDO strategy hierarchy. Fetches the full tree via RTK
 * Query and renders it as a nested list. Mirrors HealthCheck's loading/error/data
 * branch shape.
 */
export default function RcdoTree() {
  const { data, isLoading, isError } = useGetRcdoTreeQuery();

  if (isLoading) {
    return <p className="text-gray-500">Loading strategy hierarchy…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-red-600">
        Strategy hierarchy unavailable
      </p>
    );
  }
  if (!data || data.length === 0) {
    return <p className="text-gray-500">No strategy nodes yet</p>;
  }
  return (
    <ul className="text-left">
      {data.map((root) => (
        <TreeNode key={root.id} node={root} />
      ))}
    </ul>
  );
}
