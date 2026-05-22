import { Arm, Sword, Bow, Staff, Knife, Axe, LongBow, StormStaff } from './weapon.js';

class Player {
  constructor(position, name) {
    this.life = 100;
    this.magic = 20;
    this.speed = 1;
    this.attack = 10;
    this.agility = 5;
    this.luck = 10;
    this.description = 'Игрок';
    this.weapon = new Arm();
    this.position = position;
    this.name = name;
    this.hitCount = 0;
  }

  getLuck() {
    const randomNumber = Math.random() * 100;
    return (randomNumber + this.luck) / 100;
  }

  getDamage(distance) {
    const weaponDamage = this.weapon.getDamage();
    if (distance > this.weapon.range) {
      return 0;
    }
    return (this.attack + weaponDamage) * this.getLuck() / distance;
  }

  takeDamage(damage) {
    this.life -= damage;
    if (this.life < 0) {
      this.life = 0;
    }
  }

  isDead() {
    return this.life === 0;
  }

  moveLeft(distance) {
    const actualDistance = Math.min(distance, this.speed);
    this.position -= actualDistance;
    console.log(`${this.name} двигается влево на ${actualDistance}. Позиция: ${this.position}`);
  }

  moveRight(distance) {
    const actualDistance = Math.min(distance, this.speed);
    this.position += actualDistance;
    console.log(`${this.name} двигается вправо на ${actualDistance}. Позиция: ${this.position}`);
  }

  move(distance) {
    if (distance < 0) {
      this.moveLeft(Math.abs(distance));
    } else {
      this.moveRight(distance);
    }
  }

  isAttackBlocked() {
    return this.getLuck() > (100 - this.luck) / 100;
  }

  dodged() {
    return this.getLuck() > (100 - this.agility - this.speed * 3) / 100;
  }

  takeAttack(damage) {
    if (this.isAttackBlocked()) {
      console.log(`${this.name} блокирует атаку! Урон приходится по оружию.`);
      this.weapon.takeDamage(damage);
      this.checkWeapon();
      return;
    }
    if (this.dodged()) {
      console.log(`${this.name} уклоняется от атаки!`);
      return;
    }
    this.takeDamage(damage);
    console.log(`${this.name} получает ${damage.toFixed(2)} урона. Осталось жизни: ${this.life.toFixed(2)}`);
  }

  checkWeapon() {
    if (this.weapon.isBroken()) {
      const weaponChain = this.getWeaponChain();
      const currentIndex = weaponChain.findIndex(w => w.name === this.weapon.name);
      if (currentIndex >= 0 && currentIndex < weaponChain.length - 1) {
        const oldWeapon = this.weapon.name;
        this.weapon = weaponChain[currentIndex + 1];
        console.log(`${this.name} ломает ${oldWeapon} и берет ${this.weapon.name}`);
      }
    }
  }

  getWeaponChain() {
    return [new Sword(), new Knife(), new Arm()];
  }

  tryAttack(enemy) {
    const distance = Math.abs(this.position - enemy.position);
    
    if (distance > this.weapon.range) {
      console.log(`${this.name} не достает до ${enemy.name} (расстояние: ${distance.toFixed(2)}, дальность оружия: ${this.weapon.range})`);
      return;
    }

    const luck = this.getLuck();
    this.weapon.takeDamage(10 * luck);
    this.checkWeapon();

    const damage = this.getDamage(distance);
    console.log(`${this.name} атакует ${enemy.name} на ${damage.toFixed(2)} урона`);
    enemy.takeAttack(damage);

    if (this.position === enemy.position) {
      console.log(`${enemy.name} отскакивает на 1 позицию вправо от удара в упор!`);
      enemy.moveRight(1);
      console.log(`${this.name} наносит двойной урон!`);
      enemy.takeAttack(damage * 2);
    }
  }

  chooseEnemy(players) {
    const alivePlayers = players.filter(p => !p.isDead() && p !== this);
    if (alivePlayers.length === 0) {
      return null;
    }
    const enemy = alivePlayers.reduce((min, p) => p.life < min.life ? p : min);
    console.log(`${this.name} выбирает цель: ${enemy.name} (жизнь: ${enemy.life.toFixed(2)})`);
    return enemy;
  }

  moveToEnemy(enemy) {
    if (!enemy) return;
    const distance = enemy.position - this.position;
    if (distance > 0) {
      this.moveRight(Math.min(distance, this.speed));
    } else if (distance < 0) {
      this.moveLeft(Math.min(Math.abs(distance), this.speed));
    }
  }

  turn(players) {
    if (this.isDead()) return;
    console.log(`\nХод ${this.name} (${this.description})`);
    const enemy = this.chooseEnemy(players);
    if (!enemy) return;
    this.moveToEnemy(enemy);
    this.tryAttack(enemy);
  }
}

class Warrior extends Player {
  constructor(position, name) {
    super(position, name);
    this.life = 120;
    this.speed = 2;
    this.attack = 10;
    this.description = 'Воин';
    this.weapon = new Sword();
  }

  getWeaponChain() {
    return [new Sword(), new Knife(), new Arm()];
  }

  takeDamage(damage) {
    if (this.life < 60 && this.getLuck() > 0.8 && this.magic > 0) {
      console.log(`${this.name} использует ману для защиты!`);
      this.magic -= damage;
      if (this.magic < 0) {
        this.life += this.magic;
        this.magic = 0;
      }
    } else {
      this.life -= damage;
    }
    if (this.life < 0) {
      this.life = 0;
    }
  }
}

class Archer extends Player {
  constructor(position, name) {
    super(position, name);
    this.life = 80;
    this.magic = 35;
    this.attack = 5;
    this.agility = 10;
    this.description = 'Лучник';
    this.weapon = new Bow();
  }

  getWeaponChain() {
    return [new Bow(), new Knife(), new Arm()];
  }

  getDamage(distance) {
    const weaponDamage = this.weapon.getDamage();
    if (distance > this.weapon.range) {
      return 0;
    }
    return (this.attack + weaponDamage) * this.getLuck() * distance / this.weapon.range;
  }
}

class Mage extends Player {
  constructor(position, name) {
    super(position, name);
    this.life = 70;
    this.magic = 100;
    this.attack = 5;
    this.agility = 8;
    this.description = 'Маг';
    this.weapon = new Staff();
  }

  getWeaponChain() {
    return [new Staff(), new Knife(), new Arm()];
  }

  takeDamage(damage) {
    if (this.magic > 50) {
      console.log(`${this.name} использует магический щит! Урон уменьшен вдвое.`);
      const reducedDamage = damage / 2;
      this.life -= reducedDamage;
      this.magic -= 12;
    } else {
      this.life -= damage;
    }
    if (this.life < 0) {
      this.life = 0;
    }
  }
}

class Dwarf extends Warrior {
  constructor(position, name) {
    super(position, name);
    this.life = 130;
    this.attack = 15;
    this.luck = 20;
    this.description = 'Гном';
    this.weapon = new Axe();
  }

  getWeaponChain() {
    return [new Axe(), new Knife(), new Arm()];
  }

  takeDamage(damage) {
    this.hitCount++;
    if (this.hitCount % 6 === 0 && this.getLuck() > 0.5) {
      console.log(`${this.name} стойко переносит удар! Урон уменьшен вдвое.`);
      damage = damage / 2;
    }
    super.takeDamage(damage);
  }
}

class Crossbowman extends Archer {
  constructor(position, name) {
    super(position, name);
    this.life = 85;
    this.attack = 8;
    this.agility = 20;
    this.luck = 15;
    this.description = 'Арбалетчик';
    this.weapon = new LongBow();
  }

  getWeaponChain() {
    return [new LongBow(), new Knife(), new Arm()];
  }
}

class Demiurge extends Mage {
  constructor(position, name) {
    super(position, name);
    this.life = 80;
    this.magic = 120;
    this.attack = 6;
    this.luck = 12;
    this.description = 'Демиург';
    this.weapon = new StormStaff();
  }

  getWeaponChain() {
    return [new StormStaff(), new Knife(), new Arm()];
  }

  getDamage(distance) {
    const baseDamage = super.getDamage(distance);
    if (this.magic > 0 && this.getLuck() > 0.6) {
      console.log(`${this.name} усиливает атаку магией!`);
      return baseDamage * 1.5;
    }
    return baseDamage;
  }
}

export { Player, Warrior, Archer, Mage, Dwarf, Crossbowman, Demiurge };
