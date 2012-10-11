package autoeq;

import autoeq.DebuffCounter.Type;

public class SpellData {
  public static final int ATTRIB_DAMAGE = 0;
  public static final int ATTRIB_MESMERIZE = 31;
  public static final int ATTRIB_MOUNT = 113;
  public static final int ATTRIB_SHRINK = 298;
  public static final int ATTRIB_AUTO_CAST = 374;
  
  private final int id;
  private final String name;
  private final String messageWhenCastOnYou;
  private final String messageWhenCastOnOther;
  private final String messageWhenFades;
  private final int range;
  private final int aeRange;
  private final int mana;
  private final int castTime;
  
  private final float[] base = new float[12];
  private final float[] base2 = new float[12];
  private final int[] max = new int[12];
  private final int[] calc = new int[12];
  private final int[] attrib = new int[12];
  private final int targetType;
  private final int warLevel;
  private final int clrLevel;
  private final int palLevel;
  private final int rngLevel;
  private final int shdLevel;
  private final int druLevel;
  private final int mnkLevel;
  private final int brdLevel;
  private final int shortBuff;
  
  public SpellData(String[] fields) {
    this.id = Integer.parseInt(fields[0]);
    this.name = fields[1];
    this.messageWhenCastOnYou = fields[6];
    this.messageWhenCastOnOther = fields[7];
    this.messageWhenFades = fields[8];
    this.range = toInt(fields[9]);
    this.aeRange = toInt(fields[10]);
    this.castTime = toInt(fields[13]);
//    this.reCastTime = Integer.parseInt(fields[15]);
//    this.durationType = Integer.parseInt(fields[16]);
//    this.durationValue = Integer.parseInt(fields[17]);
    this.mana = toInt(fields[19]);
    
    for(int i = 0; i < 12; i++) {
      base[i] = toFloat(fields[i + 20]);
      base2[i] = toFloat(fields[i + 32]);
      max[i] = toInt(fields[i + 44]);
      calc[i] = toInt(fields[i + 70]);
      attrib[i] = toInt(fields[i + 86]);
    }
    
    targetType = toInt(fields[98]);
    warLevel = toInt(fields[104]);
    clrLevel = toInt(fields[105]);
    palLevel = toInt(fields[106]);
    rngLevel = toInt(fields[107]);
    shdLevel = toInt(fields[108]);
    druLevel = toInt(fields[109]);
    mnkLevel = toInt(fields[110]);
    brdLevel = toInt(fields[111]);
    
    shortBuff = toInt(fields[154]);
  }
  
  private static float toFloat(String s) {
    if(s.trim().length() == 0) {
      return 0;
    }
    return Float.parseFloat(s);
  }
  
  private static int toInt(String s) {
    if(s.trim().length() == 0) {
      return 0;
    }
    return Integer.parseInt(s);
  }

  private boolean isStackData(int i) {
    if(!((attrib[i] == 10 && (base[i] == -6 || base[i] == 0)) ||
        (attrib[i] == 79 && base[i] > 0 && targetType == 6) ||
        (attrib[i] == 0  && base[i] < 0) ||
        (attrib[i] == 148 || attrib[i] == 149))) {
      return true;
    }
    
    return false;
  }
  
  /**
   * Returns <code>true</code> if this spell blocks the given spell.
   */
  private boolean blocks(SpellData sd) {
    for(int i = 0; i < 12; i++) {
      if(attrib[i] == 148 || attrib[i] == 149) {
        int slot = calc[i] - 200 - 1;

        if(sd.attrib[slot] == base[i]) {
          if(max[i] > 0) {
            if(sd.base[slot] < max[i]) {
              return true;
            }
          } 
          else {
            return true;
          }
        }
      }
    }
    
    return false;
  }
  
  public boolean stacksWith(SpellData sd) {
    if(sd.id == id) {
      return true;
    }
    
    for(int i = 0; i < 12; i++) {
      if(sd.attrib[i] == attrib[i] && !(attrib[i] == 254 || sd.attrib[i] == 254) && !(attrib[i] == 57 && sd.attrib[i] == 57) && !(attrib[i] == 311 && sd.attrib[i] == 311)) {
        if(isStackData(i) && sd.isStackData(i)) {
          return false;
        }
      }
    }
    
    if(blocks(sd)) {
      return false;
    }
    
    if(sd.blocks(this)) {
      return false;
    }

    return true;
  }
  
  public int getId() {
    return id;
  }
//
//  public int getMana() {
//    return mana;
//  }

  public String getMessageWhenCastOnOther() {
    return messageWhenCastOnOther;
  }

  public String getMessageWhenCastOnYou() {
    return messageWhenCastOnYou;
  }

  public String getMessageWhenFades() {
    return messageWhenFades;
  }

  public String getName() {
    return name;
  }
  
  public int getCastTime() {
    return castTime;
  }
  
  public float getBase(int index) {
    return base[index];
  }
  
  public float getBase2(int index) {
    return base2[index];
  }

  public int getMax(int index) {
    return max[index];
  }

  public int getCalc(int index) {
    return calc[index];
  }

  public int getAttrib(int index) {
    return attrib[index];
  }

  public int getAERange() {
    return aeRange;
  }
  
  public int getRange() {
    return range;
  }
  
  public int getMana() {
    return mana;
  }
  
  public boolean isShortBuff() {
    return shortBuff == -1;
  }
  
  public DebuffCounter getDebuffCounters() {
    // attrib = 36 = poison
    // attrib = 35 = disease
    // attrib = 116 = curse
    // attrib = 369 = corruption
    
    for(int i = 0; i < 12; i++) {
      int a = attrib[i];
      
      if(a == 35) {
        return new DebuffCounter(Type.DISEASE, (int)base[i]);
      }
      if(a == 36) {
        return new DebuffCounter(Type.POISON, (int)base[i]);
      }
      if(a == 116) {
        return new DebuffCounter(Type.CURSE, (int)base[i]);
      }
      if(a == 369) {
        return new DebuffCounter(Type.CORRUPTION, (int)base[i]);
      }
    }
    
    return null;
  }

  public int getBrdLevel() {
    return brdLevel;
  }

  public int getClrLevel() {
    return clrLevel;
  }

  public int getDruLevel() {
    return druLevel;
  }

  public int getMnkLevel() {
    return mnkLevel;
  }

  public int getPalLevel() {
    return palLevel;
  }

  public int getRngLevel() {
    return rngLevel;
  }

  public int getShdLevel() {
    return shdLevel;
  }

  public int getWarLevel() {
    return warLevel;
  }
  
  public boolean hasAttribute(int attribute) {
    for(int i = 0; i < 12; i++) {
      if(getAttrib(i) == attribute) {
        return true;
      }
    }
  
    return false;
  }
}
