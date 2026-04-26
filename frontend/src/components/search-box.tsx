"use client";

import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { pushRecentSearch } from "@/lib/recent-searches";

interface Props {
  initialQuery?: string;
  /** 랜딩에서만 true. 결과 페이지에선 input focus 가 모바일 키보드를 띄워 결과를 가린다. */
  autoFocus?: boolean;
}

const PLACEHOLDER = "예: 부당하게 해고당했어요";

export function SearchBox({ initialQuery = "", autoFocus = false }: Props) {
  const router = useRouter();
  const inputRef = useRef<HTMLInputElement>(null);
  const [value, setValue] = useState(initialQuery);

  const onSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const q = value.trim();
    if (q.length === 0) return;
    pushRecentSearch(q);
    // 모바일에서 결과가 키보드에 가리지 않도록 명시적 blur. (분석 §R9)
    inputRef.current?.blur();
    router.push(`/search?q=${encodeURIComponent(q)}`);
  };

  return (
    <form
      onSubmit={onSubmit}
      role="search"
      className="flex w-full gap-2 sm:gap-3"
    >
      <Input
        ref={inputRef}
        type="search"
        inputMode="search"
        enterKeyHint="search"
        name="q"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={PLACEHOLDER}
        autoFocus={autoFocus}
        aria-label="자연어 검색어"
        className="h-12 flex-1 text-base"
      />
      <Button type="submit" size="lg" className="h-12 shrink-0 px-5">
        검색
      </Button>
    </form>
  );
}
