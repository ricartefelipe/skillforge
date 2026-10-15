import type { NextConfig } from "next";

const apiUrl = process.env.API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  agentRules: false,
  experimental: {
    serverActions: {
      bodySizeLimit: "10mb",
    },
  },
  async rewrites() {
    return [
      {
        source: "/health/liveness",
        destination: `${apiUrl}/actuator/health/liveness`,
      },
      {
        source: "/health/readiness",
        destination: `${apiUrl}/actuator/health/readiness`,
      },
    ];
  },
};

export default nextConfig;
