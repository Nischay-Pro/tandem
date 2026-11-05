/**
 * Display metadata copied from Actual's MIT-licensed currencies.ts bundled in
 * @actual-app/api 26.11.0. Update when that pinned dependency changes.
 */
export interface ActualCurrencyDisplayMetadata {
  symbol: string;
  decimalPlaces: number;
  numberFormat: string;
  symbolFirst: boolean;
}

export const ACTUAL_CURRENCY_DISPLAY_METADATA: Readonly<
  Record<string, ActualCurrencyDisplayMetadata>
> = {
  AED: {
    symbol: "د.إ",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: false,
  },
  ARS: {
    symbol: "Arg$",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: true,
  },
  AUD: {
    symbol: "A$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  BRL: {
    symbol: "R$",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: true,
  },
  BYN: {
    symbol: "Br",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
  CAD: {
    symbol: "CA$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  CHF: {
    symbol: "Fr.",
    decimalPlaces: 2,
    numberFormat: "apostrophe-dot",
    symbolFirst: true,
  },
  CLP: {
    symbol: "CLP$",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: true,
  },
  CNY: {
    symbol: "¥",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  COP: {
    symbol: "Col$",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: true,
  },
  CRC: {
    symbol: "₡",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: true,
  },
  CZK: {
    symbol: "Kč",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
  DKK: {
    symbol: "kr",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: false,
  },
  DOP: {
    symbol: "RD$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  EGP: {
    symbol: "ج.م",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: false,
  },
  EUR: {
    symbol: "€",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: false,
  },
  GBP: {
    symbol: "£",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  GTQ: {
    symbol: "Q",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  HKD: {
    symbol: "HK$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  HUF: {
    symbol: "Ft",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
  IDR: {
    symbol: "Rp",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: true,
  },
  ILS: {
    symbol: "₪",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  INR: {
    symbol: "₹",
    decimalPlaces: 2,
    numberFormat: "comma-dot-in",
    symbolFirst: true,
  },
  IRR: {
    symbol: "﷼",
    decimalPlaces: 0,
    numberFormat: "comma-dot",
    symbolFirst: false,
  },
  JMD: {
    symbol: "J$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  JPY: {
    symbol: "¥",
    decimalPlaces: 0,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  KRW: {
    symbol: "₩",
    decimalPlaces: 0,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  LKR: {
    symbol: "Rs.",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  MDL: {
    symbol: "L",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: false,
  },
  MKD: {
    symbol: "ден",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: false,
  },
  MXN: {
    symbol: "$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  MYR: {
    symbol: "RM",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  PEN: {
    symbol: "S/",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  PHP: {
    symbol: "₱",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  PKR: {
    symbol: "Rs.",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  PLN: {
    symbol: "zł",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
  QAR: {
    symbol: "ر.ق",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: false,
  },
  RON: {
    symbol: "lei",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: false,
  },
  RSD: {
    symbol: "дин",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: false,
  },
  RUB: {
    symbol: "₽",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
  SAR: {
    symbol: "ر.س",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: false,
  },
  SEK: {
    symbol: "kr",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
  SGD: {
    symbol: "S$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  THB: {
    symbol: "฿",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  TRY: {
    symbol: "₺",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: true,
  },
  TWD: {
    symbol: "NT$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  UAH: {
    symbol: "₴",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
  USD: {
    symbol: "$",
    decimalPlaces: 2,
    numberFormat: "comma-dot",
    symbolFirst: true,
  },
  UYU: {
    symbol: "$U",
    decimalPlaces: 2,
    numberFormat: "dot-comma",
    symbolFirst: true,
  },
  UZS: {
    symbol: "UZS",
    decimalPlaces: 2,
    numberFormat: "space-comma",
    symbolFirst: false,
  },
};
