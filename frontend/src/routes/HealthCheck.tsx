import { useGetHealthQuery } from '../store/api';

/**
 * Walking-skeleton route: fetches the backend `/health` via RTK Query and renders
 * the result, proving DB → security → remote → RTK Query end to end.
 */
export default function HealthCheck() {
  const { data, isLoading, isError } = useGetHealthQuery();

  if (isLoading) {
    return <p className="text-gray-500">Checking backend…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-red-600">
        Backend unavailable
      </p>
    );
  }
  return <p className="text-gray-700">Backend status: {data?.status}</p>;
}
