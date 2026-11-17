const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const script=fs.readFileSync('app/src/main/assets/claude-usage.js','utf8').replace('__RESULT_KEY__','"result"');
async function run(responses,origin='https://claude.ai'){
 const calls=[],c={location:{origin},AbortController,setTimeout,clearTimeout,Date,Set,Number};c.window=c;c.top=c;
 c.fetch=async(path,options)=>{calls.push({path,options});const r=responses.shift();if(r instanceof Error)throw r;return{ok:r.status===200,status:r.status,headers:{get:()=>r.type||'application/json'},json:async()=>r.body};};
 vm.runInNewContext(script,c);for(let i=0;i<40&&c.result===null;i++)await new Promise(r=>setImmediate(r));return{result:c.result,calls};
}
(async()=>{
 let x=await run([{status:200,body:[{uuid:'org-1',name:'Personal'},{uuid:'org-2',name:'Team'},{uuid:'org-1'}]},{status:200,body:{five_hour:{utilization:35,resets_at:'2026-11-18T12:00:00Z'},seven_day:{utilization:10},extra_usage:{is_enabled:true,monthly_limit:1000,used_credits:200,currency:'USD'},token:'must-not-copy'}},{status:403}]);
 assert.equal(x.result.rows.length,2);assert.equal(x.result.rows[0].session.utilization,35);assert.equal(x.result.rows[0].extra,'Extra usage budget left: 8.00 USD');assert.equal(x.result.rows[1].error,'Stale · HTTP 403');assert(!JSON.stringify(x.result).includes('must-not-copy'));
 for(const c of x.calls){assert.equal(c.options.credentials,'include');assert.equal(c.options.mode,'same-origin');assert.equal(c.options.redirect,'error');assert(!('Authorization' in c.options.headers));}
 x=await run([{status:403}]);assert.equal(x.result.error,'HTTP 403');
 x=await run([{status:200,type:'text/html',body:'challenge'}]);assert.equal(x.result.error,'Non-JSON response');
 x=await run([new Error('secret=abcdef')]);assert(!JSON.stringify(x.result).includes('abcdef'));
 x=await run([{status:200,body:[]}]);assert.equal(x.result.rows.length,0);
 x=await run([{status:200,body:[{uuid:'../unsafe'}]}]);assert.equal(x.calls.length,1);assert.equal(x.result.error,'Unexpected organization response');
 x=await run([{status:200,body:[{uuid:'org-1'}]},{status:200,body:{unexpected:'payload'}}]);assert.equal(x.result.rows[0].updated,undefined);assert(x.result.rows[0].error);
 x=await run([{status:200,body:[{uuid:'org-1'}]},{status:200,body:{five_hour:{utilization:101},seven_day:null}}]);assert.equal(x.result.rows[0].session.utilization,null);
 x=await run([],'https://evil.example');assert.equal(x.calls.length,0);assert.equal(x.result,undefined);
 console.log('9 Claude browser scenarios passed (mock fetch, not live Claude)');
})().catch(e=>{console.error(e);process.exitCode=1;});
