import HealthCheck from './routes/HealthCheck';
import RcdoTree from './routes/RcdoTree';

export default function App() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="space-y-4 text-center">
        <h1 className="text-2xl font-semibold text-gray-800">Weekly Commit</h1>
        <HealthCheck />
        <section className="space-y-2">
          <h2 className="text-lg font-medium text-gray-700">Strategy hierarchy</h2>
          <RcdoTree />
        </section>
      </div>
    </main>
  );
}
