import React from "react";
import { useNavigate } from "react-router-dom";
import { Image } from "@heroui/image";
import { Cast } from "@mynaui/icons-react";
import { Server, Clapperboard } from "lucide-react";
import { PersonFill } from "@gravity-ui/icons";
import { Card, CardBody, CardFooter } from "@heroui/card";
import { Spacer } from "@heroui/spacer";
import { Chip } from "@heroui/chip";
import { useTranslation } from "react-i18next";
import { useOpenExternalUrl } from "@/hooks/useOpenExternalUrl";

export default function ExtensionPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { openExternalUrl } = useOpenExternalUrl();

  return (
    <div className="flex justify-center">
      <div className="flex flex-col w-full max-w-[800px] py-6 px-10 gap-6 font-poppins">
        <h1 className="text-3xl text-white font-bold leading-9">{t("extension.title")}</h1>

        <div className="gap-4 grid grid-cols-2">
          {/* 窗口模式 */}
          <Card
            key="window"
            isHoverable
            isPressable
            className="transition-all duration-500 hover:scale-102"
            classNames={{
              footer: "p-5",
            }}
            shadow="sm"
            onPress={() => {
              navigate("/extension/window");
            }}
          >
            <CardBody className="overflow-visible p-0">
              <Image
                className="w-full object-cover h-[180px]"
                radius="lg"
                src="/assets/window-mode-preview.png"
                width="100%"
              />
            </CardBody>
            <CardFooter className="flex flex-col gap-2">
              <div className="w-full flex justify-between">
                <span className="text-lg text-white font-bold leading-6">
                  {t("extension.windowMode.title")}
                </span>
                <Cast />
              </div>
              <p className="!text-left text-sm text-default-500 w-full">
                {t("extension.windowMode.desc")}
              </p>
            </CardFooter>
          </Card>

          {/* 页面部署 */}
          <Card
            key="deployment"
            isHoverable
            isPressable
            className="transition-all duration-500 hover:scale-102"
            classNames={{
              footer: "p-5",
            }}
            shadow="sm"
            onPress={() => {
              navigate("/extension/deployment");
            }}
          >
            <CardBody className="overflow-visible p-0">
              <Image
                className="w-full object-cover h-[180px]"
                radius="lg"
                src="/assets/page-deployment-preview.png"
                width="100%"
              />
            </CardBody>
            <CardFooter className="flex flex-col gap-2">
              <div className="w-full flex justify-between">
                <span className="text-lg text-white font-bold leading-6">
                  {t("extension.deployment.title")}
                </span>
                <Server size={22} strokeWidth={1.5} />
              </div>
              <p className="!text-left text-sm text-default-500 w-full">
                {t("extension.deployment.desc")}
              </p>
            </CardFooter>
          </Card>

          {/* PV Tool */}
          <Card
            key="pv-tool"
            isHoverable
            isPressable
            className="transition-all duration-500 hover:scale-102"
            classNames={{
              footer: "p-5",
            }}
            shadow="sm"
            onPress={() => {
              openExternalUrl("https://pv.pixjam.cn/?np=1&t=3");
            }}
          >
            <CardBody className="overflow-visible p-0 relative">
              <Image
                className="w-full object-cover h-[180px]"
                radius="lg"
                src="/assets/pv-tool-preview.png"
                width="100%"
              />
              <Chip
                className="absolute top-3.5 left-4 z-10 backdrop-blur-md bg-black/15"
                size="sm"
                variant="flat"
              >
                <span className="flex items-center gap-1">
                  <PersonFill width={16} height={16} />
                  <span>索拉里斯星__</span>
                </span>
              </Chip>
            </CardBody>
            <CardFooter className="flex flex-col gap-2">
              <div className="w-full flex justify-between">
                <span className="text-lg text-white font-bold leading-6">
                  {t("extension.pvTool.title")}
                </span>
                <Clapperboard size={22} strokeWidth={1.5} />
              </div>
              <p className="!text-left text-sm text-default-500 w-full">
                {t("extension.pvTool.desc")}
              </p>
            </CardFooter>
          </Card>
        </div>

        <Spacer y={2} />
      </div>
    </div>
  );
}
