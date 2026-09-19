/** @type {import('next').NextConfig} */
const nextConfig = {
  // standalone: a imagem de producao leva so o runtime necessario, sem node_modules inteiro.
  output: 'standalone',
  reactStrictMode: true,
  poweredByHeader: false,
  typedRoutes: true,
};

export default nextConfig;
