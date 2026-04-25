import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";

const pretendardGov = localFont({
  src: [
    {
      path: "../../public/fonts/PretendardGOV-Regular.subset.woff2",
      weight: "400",
      style: "normal",
    },
    {
      path: "../../public/fonts/PretendardGOV-Medium.subset.woff2",
      weight: "500",
      style: "normal",
    },
    {
      path: "../../public/fonts/PretendardGOV-SemiBold.subset.woff2",
      weight: "600",
      style: "normal",
    },
    {
      path: "../../public/fonts/PretendardGOV-Bold.subset.woff2",
      weight: "700",
      style: "normal",
    },
  ],
  variable: "--font-pretendard-gov",
  display: "swap",
  preload: true,
  fallback: [
    "-apple-system",
    "BlinkMacSystemFont",
    "system-ui",
    "Roboto",
    "Helvetica Neue",
    "Segoe UI",
    "sans-serif",
  ],
});

export const metadata: Metadata = {
  title: "laborcase — 노동법 조문 검색",
  description:
    "한국 노동 관련 6개 법령에서 자연어로 조문을 찾을 수 있는 정보 제공 도구입니다. 법률 자문이 아닙니다.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="ko"
      className={`${pretendardGov.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col font-sans">{children}</body>
    </html>
  );
}
