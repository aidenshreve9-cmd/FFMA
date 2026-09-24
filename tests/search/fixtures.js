"use strict";
// Deterministic local fixtures. No real people: names and numbers are synthetic.
const S = require("../../prototype/search.js");

const SOUNDS = [
  ["white", "White Noise"], ["pink", "Pink Noise"], ["brown", "Brown Noise"], ["green", "Green Noise"],
  ["grey", "Grey Noise"], ["blue", "Blue Noise"], ["violet", "Violet Noise"], ["black", "Black Noise"],
].map(([id, title]) => ({ id: "sound-" + id, type: "sound", title, searchableFields: { title } }));

const SCENES = [
  "Quantum Nebula", "Spiral Galaxy", "Event Horizon", "Aurora Veil",
  "Cosmic Dust", "Stellar Nursery", "Dark Matter Web", "Ethereal Void",
].map((title, i) => ({ id: "scene-" + i, type: "scene", title, searchableFields: { title } }));

// The spec's relevance corpus.
const CONTACTS = [
  ["c-alice", "Alice", "+1 403 555 0101"],
  ["c-alicia", "Alicia", "(403) 555-0102"],
  ["c-alex", "Alex", "403.555.0103"],
  ["c-grandma", "Grandma", "+1 780 555 1234"],
  ["c-grandpa", "Grandpa", "+1 587 555 4321"],
  ["c-robert", "Robert", "780-555-9876"],
  ["c-rob", "Rob", "+44 20 7946 0958"],
  ["c-robin", "Robin", "250 555 0199"],
].map(([id, name, phone], i) => ({
  id, type: "trusted-contact", title: name, subtitle: phone, createdAt: i,
  searchableFields: { name, phone },
}));

function controller(options) {
  const c = new S.SearchController(options);
  c.define("sounds", { title: "title" });
  c.define("scenes", { title: "title" });
  c.define("trustedContacts", { name: "name", phone: "phone" });
  c.load("sounds", SOUNDS);
  c.load("scenes", SCENES);
  c.load("trustedContacts", CONTACTS);
  return c;
}

module.exports = { S, SOUNDS, SCENES, CONTACTS, controller };
