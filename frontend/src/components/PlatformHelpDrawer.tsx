import React from "react";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerBody,
  DrawerFooter,
} from "@heroui/drawer";
import { Button } from "@heroui/button";
import { Spacer } from "@heroui/spacer";
import { Image } from "@heroui/image";
import { Divider } from "@heroui/divider";
import { Card, CardHeader, CardBody } from "@heroui/card";
import { Chip } from "@heroui/chip";
import { Link } from "@heroui/link";
import { useTranslation } from "react-i18next";

import { PLATFORM_MAP } from "@/constants/platformMap";
import { useOpenExternalUrl } from "@/hooks/useOpenExternalUrl";

interface PlatformHelpDrawerProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  platform: string;
  smtc: boolean;
  isConnected: boolean;
}

const NeteaseMusicHelpContent: React.FC = () => {
  const { t } = useTranslation();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp1")}</li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp2")}</li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
    </>
  );
};

const QQMusicSmtcTrueHelpContent: React.FC = () => {
  const { t } = useTranslation();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">{t("platformHelp.qqHelp1")}</li>
        <li className="ps-1 leading-8">{t("platformHelp.qqHelp2")}</li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
      <Spacer y={6} />
      <Image radius="md" src="/assets/qq-smtc-help.png" />
    </>
  );
};

const KuGouMusicSmtcTrueHelpContent: React.FC = () => {
  const { t } = useTranslation();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">{t("platformHelp.kugouHelp1")}</li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
      <Spacer y={6} />
      <Image radius="md" src="/assets/kugou-smtc-help.png" />
    </>
  );
};

const AppleMusicSmtcFalseHelpContent: React.FC = () => {
  const { t } = useTranslation();
  const { openExternalUrl } = useOpenExternalUrl();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">
          {t("platformHelp.appleHelp1")}{" "}
          <Chip size="sm" variant="faded">
            1.6.3
          </Chip>
        </li>
        <li className="ps-1 leading-8">
          {t("platformHelp.appleHelp2")}
        </li>
        <li className="ps-1 leading-8">
          {t("platformHelp.appleHelp3")}{" "}
          <Link
            className="cursor-pointer"
            showAnchorIcon
            onPress={() => {openExternalUrl("https://www.bilibili.com/video/BV1Ae6tYdEwa/?t=36s");}}
          >
            Bilibili
          </Link>
        </li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
    </>
  );
};

const MieboHelpContent: React.FC = () => {
  const { t } = useTranslation();
  const { openExternalUrl } = useOpenExternalUrl();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">
          <Link
            className="cursor-pointer"
            showAnchorIcon
            onPress={() => {openExternalUrl("https://kdocs.cn/l/cck2G9Pjp5K4");}}
          >
            {t("platformHelp.mieboHelp1")}
          </Link>
        </li>
        <li className="ps-1 leading-8">
          {t("platformHelp.mieboHelp2")}
        </li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
      <Spacer y={6} />
      <Image radius="md" src="/assets/miebo-help.png" />
    </>
  );
};

const AynaLivePlayerHelpContent: React.FC = () => {
  const { t } = useTranslation();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">
          {t("platformHelp.aynaHelp1")}
        </li>
        <li className="ps-1 leading-8">{t("platformHelp.aynaHelp2")}</li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
      <Spacer y={6} />
      <Image radius="md" src="/assets/ayna-help.png" />
    </>
  );
};

const PotPlayerSmtcTrueHelpContent: React.FC = () => {
  const { t } = useTranslation();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">
          {t("platformHelp.potplayerHelp1")}{" "}
          <Chip size="sm" variant="faded">
            240618
          </Chip>{" "}
          <Link
            isExternal
            showAnchorIcon
            href="http://www.potplayercn.com/download"
          >
            PotPlayer
          </Link>
        </li>
        <li className="ps-1 leading-8">
          {t("platformHelp.potplayerHelp2")}
        </li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
      <Spacer y={6} />
      <Image radius="md" src="/assets/potplayer-smtc-help.png" />
    </>
  );
};

const AimpSmtcTrueHelpContent: React.FC = () => {
  const { t } = useTranslation();
  const { openExternalUrl } = useOpenExternalUrl();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">
          <Link
            className="cursor-pointer"
            showAnchorIcon
            onPress={() => {openExternalUrl("https://www.kdocs.cn/l/clIiFQrPhHfW");}}
          >
            {t("platformHelp.aimpHelp1")}
          </Link>
        </li>
        <li className="ps-1 leading-8">{t("platformHelp.neteaseHelp3")}</li>
      </ul>
    </>
  );
};

const DefaultHelpContent: React.FC = () => {
  const { t } = useTranslation();
  return (
    <>
      <ul className="list-disc flex flex-col gap-2 ml-4">
        <li className="ps-1 leading-8">
          {t("platformHelp.defaultHelp1")}
        </li>
        <li className="ps-1 leading-8">
          {t("platformHelp.defaultHelp2")}
        </li>
      </ul>
    </>
  );
};

// 生成复合键的函数，将 platform 和 smtc 组合成唯一标识
const getContentKey = (platform: string, smtc: boolean) =>
  `${platform}-${smtc}`;

// 帮助内容映射表，使用复合键作为索引
const helpContentMap: Record<string, React.FC> = {
  [getContentKey("netease", true)]: NeteaseMusicHelpContent,
  [getContentKey("netease", false)]: NeteaseMusicHelpContent,
  [getContentKey("qq", true)]: QQMusicSmtcTrueHelpContent,
  [getContentKey("kugou", true)]: KuGouMusicSmtcTrueHelpContent,
  [getContentKey("apple", false)]: AppleMusicSmtcFalseHelpContent,
  [getContentKey("miebo", true)]: MieboHelpContent,
  [getContentKey("miebo", false)]: MieboHelpContent,
  [getContentKey("ayna", true)]: AynaLivePlayerHelpContent,
  [getContentKey("ayna", false)]: AynaLivePlayerHelpContent,
  [getContentKey("potplayer", true)]: PotPlayerSmtcTrueHelpContent,
  [getContentKey("aimp", true)]: AimpSmtcTrueHelpContent,
};

const PlatformHelpDrawer: React.FC<PlatformHelpDrawerProps> = ({
  isOpen,
  onOpenChange,
  platform,
  smtc,
  isConnected,
}) => {
  const { t } = useTranslation();
  const contentKey = getContentKey(platform, smtc);

  const HelpContentComponent =
    helpContentMap[contentKey] ||
    ((props) => <DefaultHelpContent {...props} />);

  return (
    <Drawer isOpen={isOpen} size="lg" onOpenChange={onOpenChange} className="font-poppins">
      <DrawerContent>
        {(onClose) => (
          <>
            <DrawerHeader className="flex flex-col gap-1">
              <h1 className="text-2xl text-default-800 font-bold leading-12">
                {t("platformHelp.title")}
              </h1>
            </DrawerHeader>
            <DrawerBody>
              <div className="flex flex-col gap-6">
                <Card className="px-4 py-2">
                  <CardHeader>
                    <span className="text-small text-default-500">
                      {t("platformHelp.currentStatus")}
                    </span>
                  </CardHeader>
                  <Divider className="my-1" />
                  <CardBody>
                    <div className="flex flex-col gap-3">
                      <div className="flex w-full items-center justify-between">
                        <span>{t("platformHelp.musicPlatform")}</span>
                        <Chip
                          color={isConnected ? "success" : "warning"}
                          variant="bordered"
                        >
                          {PLATFORM_MAP[platform] ?? platform}
                        </Chip>
                      </div>
                      <div className="flex w-full items-center justify-between">
                        <span>{t("platformHelp.preferSmtc")}</span>
                        <Chip
                          color={isConnected ? "success" : "warning"}
                          variant="bordered"
                        >
                          {smtc ? t("platformHelp.enabled") : t("platformHelp.disabled")}
                        </Chip>
                      </div>
                    </div>
                  </CardBody>
                </Card>
                <Card className="px-4 py-2">
                  <CardHeader>
                    <span className="text-small text-default-500">
                      {t("platformHelp.solution")}
                    </span>
                  </CardHeader>
                  <Divider className="my-1" />
                  <CardBody>
                    <HelpContentComponent />
                  </CardBody>
                </Card>
              </div>
            </DrawerBody>
            <DrawerFooter>
              <Button color="default" variant="flat" onPress={onClose}>
                {t("common.close")}
              </Button>
            </DrawerFooter>
          </>
        )}
      </DrawerContent>
    </Drawer>
  );
};

export default PlatformHelpDrawer;
