#!/usr/bin/env python3
"""Run production manual-session and presentation methods with fake Android boundaries."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'src/me/jxl/kiosk/plugins/musicscreensaver/MusicScreensaverPlugin.java').read_text()
methods=s[s.index('    private void updatePresentation()'):s.index('    private void showOverlay()')]
java='''import java.util.*;
public class ManualDisplayTest {
 boolean allowed=false,manualActive,autoInhibited,userDismissed,isOverlayActive,showPaused=true,onlyWhilePlaying=true,kioskScreensaverActive,nativeDreaming,musicScreensaverSession;
 String playerState="playing",activationMode="Immediately when playing",dismissedTrackId="",trackArtist="a",trackTitle="b";
 long lastScreensaverPostpone;int postponed,stopped,shown;Host host=new Host();Root rootContainer=new Root();
 static class SystemClock {static long elapsedRealtime(){return 1000;}}
 interface Done{void done(boolean ok,Object data,Object error);}
 class Host{void executeCommand(String name,Map<?,?> args,Done done){if(name.equals("stopScreensaver"))stopped++;}}
 class Root{boolean keep;void setKeepScreenOn(boolean value){keep=value;}}
 boolean isVisibilityAllowed(){return allowed;}
 void postponeKioskScreensaver(){postponed++;}
 void showOverlay(){isOverlayActive=true;shown++;}
 void hideOverlay(){isOverlayActive=false;}
 void ensureOverlayAttached(){}
 METHODS
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
  ManualDisplayTest t=new ManualDisplayTest();t.setManualActive(true);
  check(t.isOverlayActive&&t.manualActive&&t.rootContainer.keep&&t.postponed==1,"Manual start must show now and postpone native saver");
  t.playerState="idle";t.kioskScreensaverActive=false;t.updatePresentation();
  check(t.isOverlayActive,"Manual view survives stopped music and idle dismiss events");
  t.setManualActive(false);check(!t.isOverlayActive&&!t.manualActive&&t.autoInhibited&&t.stopped==1,"Explicit off must release and dismiss once");
  t.allowed=true;t.userDismissed=false;t.playerState="playing";t.kioskScreensaverActive=true;t.updatePresentation();
  check(!t.isOverlayActive,"Track/idle event must not reopen after explicit off");
  t.setManualActive(true);check(!t.autoInhibited&&t.isOverlayActive,"A new manual start must work");
  System.out.println("Manual start, priority, stop and reactivation checks passed");
 }
}'''.replace('METHODS',methods)
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'ManualDisplayTest.java';p.write_text(java)
 subprocess.run(['javac','--release','8',str(p)],check=True)
 subprocess.run(['java','-cp',tmp,'ManualDisplayTest'],check=True)
