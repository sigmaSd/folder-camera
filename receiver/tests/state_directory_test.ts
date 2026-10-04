import { strict as assert } from "node:assert";
import { stateDirectory } from "../src/state-directory.ts";
Deno.test("receiver defaults to persistent local XDG state, supports override and ignores relative XDG values", () => {
  assert.equal(
    stateDirectory(undefined, { home: "/home/test" }),
    "/home/test/.local/state/folder-camera",
  );
  assert.equal(
    stateDirectory(undefined, {
      home: "/home/test",
      xdgStateHome: "/local/state",
    }),
    "/local/state/folder-camera",
  );
  assert.equal(
    stateDirectory(undefined, { home: "/home/test", xdgStateHome: "relative" }),
    "/home/test/.local/state/folder-camera",
  );
  assert.equal(
    stateDirectory("/custom/state", {
      home: "/home/test",
      xdgStateHome: "/local/state",
    }),
    "/custom/state",
  );
});
