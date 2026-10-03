// site.tsx
import {
  Music,
  MusicCircle,
  Monitor,
  ExternalLink,
  Heart,
  InfoCircle,
  Api,
  Grid,
  ArrowUpRight
} from "@mynaui/icons-react";
import { ListMusic } from "lucide-react";

import { NavItem } from "@/types/nav";
import { CameraIcon, SettingsIcon } from "@/components/Icons";

export type SocialInfo = {
  qqGroupNumber: string;
  qqGroupLink: string;
  githubStars: string;
};

export type SiteConfig = typeof siteConfig;

export const siteConfig = {
  name: "Now Playing",
  description: "Now Playing - Stream music & lyric display widget",
  sidebarNavItems: [
    {
      key: "general",
      label: "General",
      icon: <SettingsIcon size={24} />,
      href: "/settings/general",
    },
    {
      key: "widget",
      label: "Song Widget",
      icon: <Music size={24} />,
      href: "http://localhost:9863/settings/widget",
      external: true,
      endContent: <ArrowUpRight size={22} color="#a1a1aa" />,
    },
    {
      key: "lyric",
      label: "Lyric Widget",
      icon: <ListMusic size={24} strokeWidth={1.5} />,
      href: "/settings/lyric",
    },
    {
      key: "player",
      label: "Player",
      icon: <MusicCircle size={24} className="scale-105" />,
      href: "/settings/player",
    },
    {
      key: "desktop",
      label: "Desktop Widget",
      icon: <Monitor size={24} />,
      href: "/settings/desktop",
    },
    {
      key: "camera",
      label: "Virtual Camera",
      icon: <CameraIcon size={24} />,
      href: "/settings/camera",
    },
    {
      key: "output",
      label: "Song Info Output",
      icon: <ExternalLink size={23} width={24} />,
      href: "/settings/output",
    },
    {
      key: "apiPage",
      label: "API",
      icon: <Api size={24} />,
      href: "/apiPage",
    },
    {
      key: "extension",
      label: "Extensions",
      icon: <Grid size={24} />,
      href: "/extension",
    },
    {
      key: "sponsor",
      label: "Sponsor",
      icon: <Heart size={24} />,
      href: "/sponsor",
      position: "bottom",
    },
    {
      key: "about",
      label: "About",
      icon: <InfoCircle size={24} />,
      href: "/about",
      position: "bottom",
    },
  ] as NavItem[],
  links: {
    github: "https://github.com/Widdit/now-playing-service",
    bilibili: "https://space.bilibili.com/629593183",
    download: "https://gitee.com/widdit/now-playing/releases",
  },
  socialInfo: {
    qqGroupNumber: "150453391",
    qqGroupLink: "https://qm.qq.com/q/hWUZ7bzdS2",
    githubStars: "500",
  } satisfies SocialInfo,
};
