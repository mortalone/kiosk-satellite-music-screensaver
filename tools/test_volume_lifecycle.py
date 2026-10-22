#!/usr/bin/env python3
"""Execute the production HA volume block without needing an Android device.

Only the view boundary is stubbed. This is not an Android lifecycle integration test.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'src/me/jxl/kiosk/plugins/musicscreensaver/MusicScreensaverPlugin.java').read_text()
block = source.split('        Object vol = attrs.get("volume_level");', 1)[1].split('        Object queue =', 1)[0]
block = 'Object vol = attrs.get("volume_level");' + block
java = '''import java.util.*;
public class VolumeLifecycleTest {
    float currentVolume;
    String fluidQuality="High", vinylLight="Music";
    FakeView visualView;
    static class FakeView {
        float volume=-1;
        void setVolumeLevel(float v){volume=v;}
        void setFluidQuality(String v){}
        void setVinylLight(String v){}
    }
    void update(Map<String,Object> attrs){ BLOCK }
    public static void main(String[] args){
        VolumeLifecycleTest p=new VolumeLifecycleTest();
        p.update(Collections.<String,Object>singletonMap("volume_level",.42));
        if(Math.abs(p.currentVolume-.42f)>.001f)throw new AssertionError("Hidden overlay loses volume");
        p.visualView=new FakeView();
        p.update(Collections.<String,Object>singletonMap("volume_level",.65));
        if(Math.abs(p.visualView.volume-.65f)>.001f)throw new AssertionError("Visible view not updated");
        p.visualView=null;
        p.update(Collections.<String,Object>singletonMap("volume_level",.2));
        p.update(Collections.<String,Object>emptyMap());
        System.out.println("Volume updates before creation and after teardown passed");
    }
}'''.replace('BLOCK', block)
with tempfile.TemporaryDirectory() as tmp:
    path=Path(tmp)/'VolumeLifecycleTest.java'
    path.write_text(java)
    subprocess.run(['javac','--release','8',str(path)],check=True)
    subprocess.run(['java','-cp',tmp,'VolumeLifecycleTest'],check=True)
