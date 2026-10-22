#!/usr/bin/env python3
"""Exercise production attachment retry control flow with fake Android boundaries."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'src/me/jxl/kiosk/plugins/musicscreensaver/MusicScreensaverPlugin.java').read_text()
method=s[s.index('    private void ensureOverlayAttached()'):s.index('    private void hideOverlay()')]
java='''public class OverlayRetryTest {
 Object host=new Object(); boolean isOverlayActive=true; long lastOverlayAttempt;
 Activity activity; Root rootContainer; int builds; String status;
 static class SystemClock {static long now=1000;static long elapsedRealtime(){return now;}}
 static class Activity {boolean hasWindowFocus(){return true;}}
 static class Root {boolean attached=true;boolean isAttachedToWindow(){return attached;}boolean isShown(){return attached;}void bringToFront(){}Object getParent(){return attached?this:null;}}
 Activity activeKioskActivity(){return activity;}
 void overlayStatus(String value){status=value;}
 void rebuildOverlayViews(Activity a){builds++;rootContainer=new Root();}
 void postponeKioskScreensaver(){}
 METHOD
 public static void main(String[] args){
  OverlayRetryTest t=new OverlayRetryTest();t.ensureOverlayAttached();
  if(t.builds!=0||t.status==null)throw new AssertionError("Missing activity should wait");
  SystemClock.now=3000;t.activity=new Activity();t.ensureOverlayAttached();
  if(t.builds!=1)throw new AssertionError("Retry must attach after late activity");
  t.ensureOverlayAttached();if(t.builds!=1)throw new AssertionError("Attached view must not rebuild each tick");
  t.rootContainer.attached=false;SystemClock.now=5000;t.ensureOverlayAttached();
  if(t.builds!=2)throw new AssertionError("Detached view should be restored");
  t.isOverlayActive=false;t.rootContainer=null;SystemClock.now=7000;t.ensureOverlayAttached();
  if(t.builds!=2)throw new AssertionError("Cancelled request must not reopen");
  System.out.println("Overlay late-activity, detachment and cancellation checks passed");
 }
}'''.replace('METHOD',method)
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'OverlayRetryTest.java';p.write_text(java)
 subprocess.run(['javac','--release','8',str(p)],check=True)
 subprocess.run(['java','-cp',tmp,'OverlayRetryTest'],check=True)
