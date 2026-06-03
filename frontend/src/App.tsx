import MyWeek from './routes/MyWeek';
import HealthCheck from './routes/HealthCheck';
import RcdoTree from './routes/RcdoTree';

export default function App() {
  return (
    <main className="flex min-h-screen justify-center bg-gray-50 p-8">
      <div className="w-full max-w-2xl space-y-8">
        <h1 className="text-2xl font-semibold text-gray-800">Weekly Commit</h1>
        {/* The IC's primary surface — adaptive on plan lifecycle status. */}
        <MyWeek />
        {/* Secondary context: backend health + strategy hierarchy. */}
        <section className="space-y-4 border-t border-gray-200 pt-6">
          <HealthCheck />
          <section className="space-y-2">
            <h2 className="text-lg font-medium text-gray-700">Strategy hierarchy</h2>
            <RcdoTree />
          </section>
        </section>
      </div>
    </main>
  );
}
