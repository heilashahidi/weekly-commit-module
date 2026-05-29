import flowbite from 'flowbite-react/tailwind';

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}', flowbite.content()],
  theme: {
    extend: {},
  },
  plugins: [flowbite.plugin()],
};
