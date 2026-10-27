// A SCORM package is untrusted teacher content. It plays inside a sandboxed, cross-origin iframe, so it
// cannot reach the parent window, its cookies or the signed-in session. The LMS runtime it expects then
// lives inside that frame: this shim answers the SCORM 1.2 and 2004 calls and relays a commit to the parent
// through postMessage, where the player — holding the session — records the progress.

export const SCORM_MESSAGE_SOURCE = "skillforge-scorm";

export type ScormCommit = {
  status: string;
  score: number | null;
};

const BRIDGE = `(function(){
var values={};
function number(raw){if(raw===undefined||raw===null||raw==="")return null;var n=Number(raw);return isNaN(n)?null:n;}
function commit(){
var status=values["cmi.core.lesson_status"]||values["cmi.completion_status"]||"incomplete";
var score=number(values["cmi.core.score.raw"]!==undefined?values["cmi.core.score.raw"]:values["cmi.score.raw"]);
try{parent.postMessage({source:"${SCORM_MESSAGE_SOURCE}",status:status,score:score},"*");}catch(e){}
return "true";
}
var api={
LMSInitialize:function(){return "true";},Initialize:function(){return "true";},
LMSFinish:function(){return commit();},Terminate:function(){return commit();},
LMSGetValue:function(k){return values[k]||"";},GetValue:function(k){return values[k]||"";},
LMSSetValue:function(k,v){values[k]=String(v);return "true";},SetValue:function(k,v){values[k]=String(v);return "true";},
LMSCommit:function(){return commit();},Commit:function(){return commit();},
LMSGetLastError:function(){return "0";},GetLastError:function(){return "0";},
LMSGetErrorString:function(){return "";},GetErrorString:function(){return "";},
LMSGetDiagnostic:function(){return "";},GetDiagnostic:function(){return "";}
};
window.API=api;window.API_1484_11=api;
})();`;

export function isHtml(contentType: string | null): boolean {
  return contentType !== null && contentType.toLowerCase().includes("text/html");
}

// Inserts the bridge as the first script the package sees, so API discovery finds it without walking out of
// the sandbox. The script lands right after <head> (or <html>, or at the front) to run before package code.
export function injectScormBridge(html: string): string {
  const tag = `<script>${BRIDGE}</script>`;
  const head = /<head[^>]*>/i.exec(html);
  if (head) {
    const at = head.index + head[0].length;
    return html.slice(0, at) + tag + html.slice(at);
  }
  const root = /<html[^>]*>/i.exec(html);
  if (root) {
    const at = root.index + root[0].length;
    return html.slice(0, at) + tag + html.slice(at);
  }
  return tag + html;
}

// Reads a commit a sandboxed package sent up. Anything that is not our own message is ignored.
export function readScormCommit(data: unknown): ScormCommit | null {
  if (data === null || typeof data !== "object") {
    return null;
  }
  const record = data as Record<string, unknown>;
  if (record.source !== SCORM_MESSAGE_SOURCE) {
    return null;
  }
  const status = typeof record.status === "string" && record.status.length > 0 ? record.status : "incomplete";
  const score = typeof record.score === "number" && Number.isInteger(record.score) ? record.score : null;
  return { status, score };
}
