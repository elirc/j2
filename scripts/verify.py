"""Build and check the library using only helper-owned temporary fixtures."""
from pathlib import Path
import json,os,shutil,subprocess,tempfile,time
ROOT=Path(__file__).resolve().parents[1]
RESULTS=Path(os.environ.get('LIBRARY_VERIFY_RESULTS_DIR',ROOT/'.verification')).resolve();RESULTS.mkdir(parents=True,exist_ok=True)
def executable(name):
 p=Path(os.environ.get('JAVA_HOME',''))/'bin'/(name+'.exe' if os.name=='nt' else name)
 return str(p) if p.is_file() else shutil.which(name)
java,javac=executable('java'),executable('javac')
if not java or not javac:raise SystemExit('JDK 17+ required; set JAVA_HOME or PATH.')
with tempfile.TemporaryDirectory(prefix='astra-library-verify-') as owned:
 fixture=Path(owned);classes=fixture/'classes';classes.mkdir();sources=sorted([*ROOT.glob('src/**/*.java'),*ROOT.glob('test/**/*.java')])
 subprocess.run([javac,'-encoding','UTF-8','--release','17','-d',str(classes),*[str(p) for p in sources]],check=True,cwd=ROOT)
 unit=subprocess.run([java,'-cp',str(classes),'librarytracker.LibraryWorkflowTest',str(fixture/'unit')],capture_output=True,text=True,encoding='utf-8')
 (RESULTS/'unit.log').write_text(unit.stdout+unit.stderr,encoding='utf-8');assert unit.returncode==0,(unit.stdout,unit.stderr);unit_result=json.loads(next(x[7:] for x in unit.stdout.splitlines() if x.startswith('RESULT ')));checks=[]
 def cli(name,text,path,expected=0):
  r=subprocess.run([java,'-cp',str(classes),'librarytracker.App',str(path)],input=text,capture_output=True,text=True,encoding='utf-8',timeout=60)
  (RESULTS/(name+'.log')).write_text(r.stdout+r.stderr,encoding='utf-8');assert r.returncode==expected,(name,r.stdout,r.stderr);checks.append(name);return r
 p=fixture/'cli';r=cli('create-and-checkout','1\nCLI book\nCLI author\n\n3\nCLI reader\nreader@example.test\n\n5\n0\n0\n\n9\n',p);assert 'checked out and saved' in r.stdout
 r=cli('restart-current-loans','7\n9\n',p);assert 'Checked out to 0' in r.stdout
 r=cli('return-and-history','6\n0\n16\n9\n',p);assert 'history retained' in r.stdout and 'CLI book' in r.stdout and 'CLI reader' in r.stdout
 r=cli('edit-and-archive','10\n0\nEdited book\nEdited author\n\n12\n0\nEdited reader\nnew@example.test\n\n17\n0\nYES\n18\n0\nYES\n14\nEdited\narchived\n9\n',p);assert 'Edited book' in r.stdout and 'Archived' in r.stdout
 r=cli('history-blocks-delete','11\n0\nDELETE\n13\n0\nDELETE\n9\n',p);assert r.stdout.count('loan history')==2
 before=(p/'library.snapshot').read_bytes();r=cli('incomplete-eof','1\nIncomplete title\n',p);assert (p/'library.snapshot').read_bytes()==before and 'incomplete command' in r.stdout
 unused=fixture/'unused';r=cli('unused-confirmed-delete','1\nUnused book\nAuthor\n\n11\n0\nno\n11\n0\nDELETE\n9\n',unused);assert 'Deletion cancelled' in r.stdout and 'Book deleted and saved' in r.stdout
 bad=fixture/'bad';bad.mkdir();(bad/'library.snapshot').write_bytes(b'bad snapshot');r=cli('corrupt-startup','9\n',bad,1);assert (bad/'library.snapshot').read_bytes()==b'bad snapshot' and 'were not replaced' in r.stderr
 race=fixture/'race';go=fixture/'go';children=[]
 for i in range(2):
  ready=fixture/('ready'+str(i));child=subprocess.Popen([java,'-cp',str(classes),'librarytracker.LibraryWorkflowTest','worker',str(race),str(ready),str(go),'Worker '+str(i)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding='utf-8');children.append((child,ready))
 try:
  deadline=time.monotonic()+60
  while not all(p.exists() for _,p in children):
   if time.monotonic()>deadline:raise RuntimeError('Worker startup timeout')
   time.sleep(.02)
  go.write_text('go',encoding='utf-8');outputs=[p.communicate(timeout=60) for p,_ in children];assert all(p.returncode==0 for p,_ in children);combined=''.join(out for out,_ in outputs);assert combined.count('ACCEPTED')==1 and combined.count('CONFLICT')==1,outputs;checks.append('two-jvm-conflict');(RESULTS/'two-jvm.log').write_text(combined,encoding='utf-8')
 finally:
  for child,_ in children:
   if child.poll() is None:child.kill();child.wait()
 evidence={'passed':True,'javaTests':unit_result['tests'],'processChecks':len(checks),'total':unit_result['tests']+len(checks),'checks':checks,'environment':'JDK 17; disposable temporary fixtures; no personal library data used'}
 (RESULTS/'results.json').write_text(json.dumps(evidence,indent=2),encoding='utf-8');print(json.dumps(evidence))
