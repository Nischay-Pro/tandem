// Records Actual's schedule next-date results (rschedule 1.5.0 + date-fns 4.4.0) for Tandem's Kotlin port.
import * as d from "../../node_modules/.pnpm/date-fns@4.4.0/node_modules/date-fns/index.js";
import console from "node:console";
import fs from "node:fs";
import { createRequire } from "node:module";
import process from "node:process";
import { URL } from "node:url";

const require = createRequire(
  new URL(
    "../../node_modules/.pnpm/@rschedule+standard-date-adapter@1.5.0_@rschedule+core@1.5.0/node_modules/@rschedule/standard-date-adapter/package.json",
    import.meta.url,
  ),
);
require("@rschedule/standard-date-adapter/setup");
const { Schedule } = require("@rschedule/core/generators");

const parse = (value) => {
  const [y, m, day] = value.split("-");
  return new Date(
    parseInt(y),
    parseInt(m) - 1,
    day == null ? 1 : parseInt(day),
    12,
  );
};
const dayFromDate = (date) => d.format(date, "yyyy-MM-dd");

function rrules(config) {
  const base = {
    start: parse(config.start),
    frequency: config.frequency.toUpperCase(),
    byHourOfDay: [12],
  };
  if (config.interval) base.interval = config.interval;
  if (config.endMode === "after_n_occurrences")
    base.count = config.endOccurrences;
  if (config.endMode === "on_date") base.end = parse(config.endDate);
  if (
    config.frequency === "monthly" &&
    config.patterns &&
    config.patterns.length > 0
  ) {
    const days = config.patterns.filter((p) => p.type === "day");
    const names = config.patterns.filter((p) => p.type !== "day");
    return [
      days.length > 0 && { ...base, byDayOfMonth: days.map((p) => p.value) },
      names.length > 0 && {
        ...base,
        byDayOfWeek: names.map((p) => [
          p.type.slice(0, 2).toUpperCase(),
          p.value,
        ]),
      },
    ].filter(Boolean);
  }
  return [base];
}

function skipWeekend(date, mode) {
  if (d.isWeekend(date))
    return mode === "after" ? d.nextMonday(date) : d.previousFriday(date);
  return date;
}

function getNextDate(config, start, noSkipWeekend = false) {
  start = d.startOfDay(start);
  const schedule = new Schedule({ rrules: rrules(config) });
  let dates = schedule.occurrences({ start, take: 1 }).toArray();
  if (dates.length === 0)
    dates = schedule.occurrences({ reverse: true, take: 1 }).toArray();
  if (dates.length === 0) return null;
  let date = dates[0].date;
  if (config.skipWeekend && !noSkipWeekend)
    date = skipWeekend(date, config.weekendSolveMode);
  return dayFromDate(date);
}

function getNextDateAfter(config, afterDate) {
  let start = d.subDays(parse(afterDate), 1);
  for (let attempt = 0; attempt < 7; attempt++) {
    const occurrence = getNextDate(config, start, true);
    if (occurrence == null || occurrence < dayFromDate(start)) return null;
    const next = getNextDate(config, start);
    if (next != null && next > afterDate) return next;
    start = d.addDays(parse(occurrence), 1);
  }
  return null;
}

function getNextDateFrom(config, today) {
  const next = getNextDate(config, parse(today));
  const moved = getNextDateAfter(
    config,
    d.format(d.subDays(parse(today), 1), "yyyy-MM-dd"),
  );
  return moved != null && (next == null || moved < next) ? moved : next;
}

const base = {
  interval: 1,
  patterns: [],
  skipWeekend: false,
  weekendSolveMode: "after",
  endMode: "never",
};
const configs = [
  { ...base, frequency: "daily", start: "2026-01-30" },
  { ...base, frequency: "daily", start: "2026-02-27", interval: 3 },
  { ...base, frequency: "weekly", start: "2026-03-07", skipWeekend: true },
  {
    ...base,
    frequency: "weekly",
    start: "2026-03-07",
    interval: 2,
    skipWeekend: true,
    weekendSolveMode: "before",
  },
  { ...base, frequency: "monthly", start: "2026-01-31" },
  {
    ...base,
    frequency: "monthly",
    start: "2026-01-31",
    interval: 2,
    skipWeekend: true,
  },
  { ...base, frequency: "monthly", start: "2026-08-02" },
  {
    ...base,
    frequency: "monthly",
    start: "2026-01-15",
    patterns: [{ type: "day", value: -1 }],
  },
  {
    ...base,
    frequency: "monthly",
    start: "2026-01-15",
    patterns: [
      { type: "day", value: 31 },
      { type: "day", value: 15 },
    ],
  },
  {
    ...base,
    frequency: "monthly",
    start: "2026-01-01",
    patterns: [
      { type: "friday", value: 2 },
      { type: "monday", value: -1 },
    ],
  },
  {
    ...base,
    frequency: "monthly",
    start: "2026-01-01",
    patterns: [
      { type: "day", value: 1 },
      { type: "sunday", value: 1 },
    ],
    skipWeekend: true,
    weekendSolveMode: "before",
  },
  { ...base, frequency: "yearly", start: "2024-02-29" },
  { ...base, frequency: "yearly", start: "2025-12-31", interval: 2 },
  {
    ...base,
    frequency: "monthly",
    start: "2026-01-10",
    endMode: "after_n_occurrences",
    endOccurrences: 4,
  },
  {
    ...base,
    frequency: "weekly",
    start: "2026-01-05",
    endMode: "on_date",
    endDate: "2026-03-01",
  },
  {
    ...base,
    frequency: "monthly",
    start: "2026-12-05",
    patterns: [{ type: "wednesday", value: 3 }],
    endMode: "after_n_occurrences",
    endOccurrences: 2,
  },
];
const days = [];
for (
  let day = parse("2025-12-20");
  day <= parse("2027-03-10");
  day = d.addDays(day, 9)
)
  days.push(dayFromDate(day));
days.push(
  "2026-02-28",
  "2026-03-01",
  "2026-11-21",
  "2026-11-22",
  "2028-02-28",
  "2028-02-29",
);
const cases = configs.map((config) => ({
  config,
  results: days.map((day) => ({
    day,
    next: getNextDate(config, parse(day)),
    from: getNextDateFrom(config, day),
    after: getNextDateAfter(config, day),
  })),
}));
fs.writeFileSync(process.argv[2], JSON.stringify(cases, null, 2) + "\n");
console.log(`${cases.length} configs, ${days.length} days`);
