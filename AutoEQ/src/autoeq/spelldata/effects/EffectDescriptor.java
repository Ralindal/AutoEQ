package autoeq.spelldata.effects;

public class EffectDescriptor {
  private final int attribCode;
  private final int base1;
  private final int base2;
  private final int max;
  private final int calc;

  public EffectDescriptor(int attribCode, int base1, int base2, int max, int calc) {
    this.attribCode = attribCode;
    this.base1 = base1;
    this.base2 = base2;
    this.max = max;
    this.calc = calc;
  }

  public int getAttribCode() {
    return attribCode;
  }

  public int getBase1() {
    return base1;
  }

  public int getBase2() {
    return base2;
  }

  public int getMax() {
    return max;
  }

  public int getCalculatedBase1(int level) {
    int tick = 10;  // assume 10th tick

    if(calc == 0) {
      return base1;
    }

    if(calc == 100) {
      if(max > 0 && base1 > max) {
        return max;
      }
      return base1;
    }

    int change = 0;

    switch(calc) {
    case 100:
      break;
    case 101:
      change = level / 2;
      break;
    case 102:
      change = level;
      break;
    case 103:
      change = level * 2;
      break;
    case 104:
      change = level * 3;
      break;
    case 105:
      change = level * 4;
      break;
    case 107:
      change = -1 * tick;
      break;
    case 108:
      change = -2 * tick;
      break;
    case 109:
      change = level / 4;
      break;
    case 110:
      change = level / 6;
      break;
    case 111:
      if(level > 16)
        change = (level - 16) * 6;
      break;
    case 112:
      if(level > 24)
        change = (level - 24) * 8;
      break;
    case 113:
      if(level > 34)
        change = (level - 34) * 10;
      break;
    case 114:
      if(level > 44)
        change = (level - 44) * 15;
      break;
    case 115:
      if(level > 15)
        change = (level - 15) * 7;
      break;
    case 116:
      if(level > 24)
        change = (level - 24) * 10;
      break;
    case 117:
      if(level > 34)
        change = (level - 34) * 13;
      break;
    case 118:
      if(level > 44)
        change = (level - 44) * 20;
      break;
    case 119:
      change = level / 8;
      break;
    case 120:
      change = -5 * tick;
      break;
    case 121:
      change = level / 3;
      break;
    case 122:
      change = -12 * tick;
      break;
    case 123:
      // random in range
      change = (Math.abs(max) - Math.abs(base1)) / 2;
      break;
    case 124:
      if(level > 50)
        change = (level - 50);
      break;
    case 125:
      if(level > 50)
        change = (level - 50) * 2;
      break;
    case 126:
      if(level > 50)
        change = (level - 50) * 3;
      break;
    case 127:
      if(level > 50)
        change = (level - 50) * 4;
      break;
    case 128:
      if(level > 50)
        change = (level - 50) * 5;
      break;
    case 129:
      if(level > 50)
        change = (level - 50) * 10;
      break;
    case 130:
      if(level > 50)
        change = (level - 50) * 15;
      break;
    case 131:
      if(level > 50)
        change = (level - 50) * 20;
      break;
    case 132:
      if(level > 50)
        change = (level - 50) * 25;
      break;
    case 139:
      if(level > 30)
        change = (level - 30) / 2;
      break;
    case 140:
      if(level > 30)
        change = (level - 30);
      break;
    case 141:
      if(level > 30)
        change = 3 * (level - 30) / 2;
      break;
    case 142:
      if(level > 30)
        change = 2 * (level - 60);
      break;
    case 143:
      change = 3 * level / 4;
      break;

    default:
      if(calc > 0 && calc < 1000)
        change = level * calc;

      // 1000..1999 variable by tick
      // e.g. splort (growing): Effect=0 Base1=1 Base2=0 Max=0 Calc=1035
      //      34 - 69 - 104 - 139 - 174 - 209 - 244 - 279 - 314 - 349 - 384 - 419 - 454 - 489 - 524 - 559 - 594 - 629 - 664 - 699 - 699
      // e.g. venonscale (decaying): Effect=0 Base1=-822 Base2=0 Max=822 Calc=1018
      //
      // e.g. Deathcloth Spore: Base1=-1000 Base2=0 Max=0 Calc=1999
      // e.g. Bleeding Bite: Base1=-1000 Base2=0 Max=0 Calc=1100 (The damage done will decrease in severity over time.)
      // e.g. Blood Rites: Base1=-1500 Base2=0 Max=0 Calc=1999
      if(calc >= 1000 && calc < 2000)
        change = tick * (calc - 1000) * -1;

      // 2000..2999 variable by level
      if(calc >= 2000)
        change = level * (calc - 2000);
      break;
    }

    int value = Math.abs(base1) + change;

    if(max != 0 && value > Math.abs(max)) {
      value = Math.abs(max);
    }

    if(base1 < 0) {
      value = -value;
    }

    return value;
  }
}
