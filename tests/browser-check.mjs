import chromium from "@sparticuz/chromium";
import puppeteer from "puppeteer-core";
import { spawn } from "node:child_process";
import { once } from "node:events";
const f = spawn(process.env.JAVA_BIN || "java", [
  "-cp",
  process.env.TEST_CLASSES || "build/test-classes",
  "app.pocketflix.BrowserFixture",
  process.argv[2],
  "src/main/assets/index.html",
]);
let b;
try {
  let [port] = await once(f.stdout, "data");
  b = await puppeteer.launch({
    args: chromium.args,
    executablePath: await chromium.executablePath(),
    headless: true,
  });
  await Promise.all(
    [1, 2].map(async (i) => {
      const p = await b.newPage();
      await p.goto("http://127.0.0.1:" + String(port).trim());
      await p.waitForSelector(".tile");
      await p.click(".tile");
      await p.waitForFunction(
        () => document.querySelector("video").currentTime > 0.4,
        { timeout: 7000, polling: 100 },
      );
      for (const t of [12, 4]) {
        await p.evaluate(
          (t) => (document.querySelector("video").currentTime = t),
          t,
        );
        await p.waitForFunction(
          (t) => {
            let v = document.querySelector("video");
            return !v.seeking && v.currentTime >= t && v.currentTime < t + 1;
          },
          { timeout: 5000, polling: 100 },
          t,
        );
      }
      await p.waitForFunction(
        () => Number(localStorage.getItem("pf:test")) >= 4,
      );
      await p.click("#close");
      await p.click("nav button:last-child");
      await p.click(".tile");
      await p.waitForFunction(
        () => document.querySelector("video").currentTime >= 4,
      );
      console.log(
        "Client " + i + ": played MP4, sought forward/backward and resumed",
      );
    }),
  );
} finally {
  if (b) await b.close();
  f.stdin.end();
}
