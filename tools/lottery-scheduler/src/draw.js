import { randomInt } from "node:crypto";

export const LOTTERY_TIME_ZONE = "America/Edmonton";
// Alberta adopted permanent UTC-6 on November 1, 2026. Apply that policy explicitly
// so machines with older timezone databases agree with updated GitHub runners.
// https://www.alberta.ca/albertas-new-time-system-abt
const PERMANENT_ALBERTA_TIME_START = Date.parse("2026-11-01T06:00:00Z");
const SIX_HOURS = 6 * 60 * 60 * 1000;

/** A stored lottery is invalid and must be reviewed rather than overwritten. */
export class DrawDataError extends Error {}

/**
 * Returns the Alberta calendar date, independent of the runner's timezone database.
 * Uses permanent UTC-6 from November 2026; earlier dates retain historical Edmonton rules.
 */
export function albertaDate(now = new Date()) {
  if (now.getTime() >= PERMANENT_ALBERTA_TIME_START) {
    return new Date(now.getTime() - SIX_HOURS).toISOString().slice(0, 10);
  }
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: LOTTERY_TIME_ZONE,
    year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(now);
  const value = (type) => parts.find((part) => part.type === type).value;
  return `${value("year")}-${value("month")}-${value("day")}`;
}

function validDate(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function userIds(value, field) {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.some((id) => typeof id !== "string" || !id.trim())) {
    throw new DrawDataError(`${field} must contain Firebase user IDs.`);
  }
  return value;
}

/** Reads the booked array as reservation objects; reservation rules remain in the app. */
function bookedReservations(value) {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.some((reservation) =>
    reservation === null || typeof reservation !== "object" || Array.isArray(reservation))) {
    throw new DrawDataError("booked must contain reservation objects.");
  }
  return value;
}

/**
 * Selects up to count distinct registered IDs without modifying the input.
 * Matches Lottery.draw: return everyone when count covers all distinct entrants.
 * Random selection uses a partial Fisher-Yates shuffle without replacement.
 */
export function drawRegistered(registered, count, randomIndex = randomInt) {
  if (!Number.isSafeInteger(count) || count < 0) {
    throw new DrawDataError("Winner count must be a non-negative integer.");
  }
  const entrants = [...new Set(userIds(registered, "registered"))];
  if (entrants.length <= count) return entrants;
  for (let index = 0; index < count; index++) {
    const selected = index + randomIndex(entrants.length - index);
    [entrants[index], entrants[selected]] = [entrants[selected], entrants[index]];
  }
  return entrants.slice(0, count);
}

/**
 * Returns only the Firestore fields to update, or null for a completed/not-yet-due draw.
 * End dates remain open through 23:59 Alberta time. Only an earlier local date is due.
 * Missing drawCompleted supports documents created before the marker was introduced.
 */
export function prepareDraw(lottery, now = new Date()) {
  if (lottery.drawCompleted === true) return null;
  if (lottery.drawCompleted !== undefined && lottery.drawCompleted !== false) {
    throw new DrawDataError("drawCompleted must be a boolean.");
  }
  if (!validDate(lottery.registrationEndDate)) {
    throw new DrawDataError("Registration end date must be a valid yyyy-MM-dd date.");
  }
  if (lottery.registrationEndDate >= albertaDate(now)) return null;
  if (!Number.isSafeInteger(lottery.numberOfWinners) || lottery.numberOfWinners <= 0) {
    throw new DrawDataError("The lottery must have a positive integer winner count.");
  }
  if (userIds(lottery.pending, "pending").length || bookedReservations(lottery.booked).length) {
    throw new DrawDataError("Unmarked lottery already has pending users or reservations; review its draw status.");
  }
  return {
    pending: drawRegistered(lottery.registered, lottery.numberOfWinners),
    drawCompleted: true,
  };
}
