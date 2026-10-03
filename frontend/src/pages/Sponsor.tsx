import React, { Suspense, useEffect, useMemo } from "react";
import {
  Table,
  TableHeader,
  TableBody,
  TableColumn,
  TableRow,
  TableCell,
  getKeyValue,
} from "@heroui/table";
import { Spinner } from "@heroui/spinner";
import { useAsyncList } from "@react-stately/data";
import { Spacer } from "@heroui/spacer";
import { Chip } from "@heroui/chip";
import { ClockCircle } from "@mynaui/icons-react";
import { Tooltip } from "@heroui/tooltip";
import { useTranslation } from "react-i18next";

import { useEnv } from "@/contexts/EnvContext";

const Lanyard = React.lazy(() => import("@/components/lanyard/Lanyard"));

type SponsorRecord = {
  key: string;
  date: string;
  sponsor: string;
  amount: string;
  message: string;
};

export default function SponsorPage() {
  const { t } = useTranslation();
  const [isError, setIsError] = React.useState(false);
  const [updateTime, setUpdateTime] = React.useState("");
  const [maxTableHeight, setMaxTableHeight] = React.useState(520);
  const { isDesktop } = useEnv();

  const columns = useMemo(() => [
    { key: "date", label: t("sponsor.date"), allowsSorting: true, width: 130 },
    { key: "sponsor", label: t("sponsor.sponsor"), allowsSorting: true, width: 120 },
    { key: "amount", label: t("sponsor.amount"), allowsSorting: true, width: 110 },
    { key: "message", label: t("sponsor.message"), allowsSorting: false },
  ], [t]);

  const list = useAsyncList<SponsorRecord>({
    async load({ signal }) {
      try {
        const res = await fetch("/api/sponsorList", { signal });

        if (!res.ok) throw new Error("Network error");
        const data = await res.json();

        setUpdateTime(data.updateTime);

        if (data.records && Array.isArray(data.records)) {
          return {
            items: data.records.map((item: any, index: number) => ({
              key: String(index + 1),
              date: item.date || "",
              sponsor: item.sponsor || "",
              amount: item.amount || "",
              message: item.message || "",
            })),
          };
        } else {
          return { items: [] };
        }
      } catch (err) {
        console.error("获取赞助列表失败:", err);
        setIsError(true);

        return { items: [] };
      }
    },

    async sort({ items, sortDescriptor }) {
      return {
        items: [...items].sort((a, b) => {
          const colKey = sortDescriptor.column as keyof SponsorRecord;
          const first = a[colKey] ?? "";
          const second = b[colKey] ?? "";

          let cmp = 0;

          if (colKey === "amount") {
            const rates: Record<string, number> = {
              "¥": 1,
              "$": 7.18,
              "€": 8.36,
              "£": 9.66,
            };

            const parseToCNY = (amountStr: string) => {
              const parts = amountStr.trim().split(" ");

              if (parts.length !== 2) return 0;
              const symbol = parts[0];
              const num = parseFloat(parts[1]) || 0;
              const rate = rates[symbol] ?? 1;

              return num * rate;
            };

            const val1 = parseToCNY(String(first));
            const val2 = parseToCNY(String(second));

            cmp = val1 < val2 ? -1 : val1 > val2 ? 1 : 0;
          } else {
            cmp =
              String(first) < String(second)
                ? -1
                : String(first) > String(second)
                  ? 1
                  : 0;
          }

          if (sortDescriptor.direction === "descending") {
            cmp *= -1;
          }

          return cmp;
        }),
      };
    },
  });

  useEffect(() => {
    const calculateTableHeight = () => {
      const windowH = window.innerHeight;

      let newTableHeight = Math.floor(windowH - 335);
      if (!isDesktop) {
        newTableHeight += 50;
      }

      setMaxTableHeight(newTableHeight);
    };

    calculateTableHeight();
    window.addEventListener("resize", calculateTableHeight);

    return () => {
      window.removeEventListener("resize", calculateTableHeight);
    };
  }, [isDesktop]);

  return (
    <div className="px-10 py-6 md:h-full md:overflow-hidden">
      <div className="flex flex-col md:flex-row gap-6 md:h-full mx-auto max-w-[1600px]">
        {/* 二维码 */}
        <div className="w-full md:w-1/2 flex flex-col md:h-full">
          <h1 className="text-3xl text-white font-bold leading-9">{t("sponsor.title")}</h1>
          <div className="relative -top-10 w-full h-full flex items-center justify-center">
            <Suspense fallback={null}>
              <Lanyard
                gravity={[0, -40, 0]}
                platform="wechat"
                position={[0, 0, 20]}
              />
            </Suspense>
          </div>
        </div>

        {/* 赞助我们 & 赞助名单 */}
        <div className="w-full md:w-1/2 max-w-[720px] flex flex-col">
          <div className="flex flex-col gap-4">
            <h1 className="text-xl text-default-800 font-bold leading-9">
              {t("sponsor.supportUs")}
            </h1>
            <p className="leading-6.5">
              {t("sponsor.desc")}
            </p>

            <Spacer x={2} />

            <div className="flex flex-row gap-4 items-center">
              <h1 className="text-xl text-default-800 font-bold leading-9">
                {t("sponsor.sponsorList")}
              </h1>
              <Tooltip closeDelay={200} content={t("sponsor.statsTime")} delay={50}>
                <Chip
                  size="sm"
                  startContent={<ClockCircle className="mx-0.5" size={16} />}
                  variant="flat"
                >
                  {updateTime}
                </Chip>
              </Tooltip>
            </div>
            <Table
              isHeaderSticky
              isVirtualized
              aria-label="Sponsor List Table"
              maxTableHeight={maxTableHeight}
              selectionMode="single"
              sortDescriptor={list.sortDescriptor}
              onSortChange={list.sort}
            >
              <TableHeader columns={columns}>
                {(column) => (
                  <TableColumn
                    key={column.key}
                    allowsSorting={column.allowsSorting}
                    width={column.width}
                  >
                    {column.label}
                  </TableColumn>
                )}
              </TableHeader>

              {isError ? (
                <TableBody emptyContent={t("sponsor.loadFailed")}>{[]}</TableBody>
              ) : (
                <TableBody
                  isLoading={list.isLoading}
                  items={list.items}
                  loadingContent={<Spinner label={t("common.loading")} />}
                >
                  {(item) => (
                    <TableRow key={item.key}>
                      {(columnKey) => (
                        <TableCell>{getKeyValue(item, columnKey)}</TableCell>
                      )}
                    </TableRow>
                  )}
                </TableBody>
              )}
            </Table>
          </div>
        </div>
      </div>
    </div>
  );
}
