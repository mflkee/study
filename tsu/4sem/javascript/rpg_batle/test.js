import { Weapon, Arm, Bow, Sword, Knife, Staff, LongBow, Axe, StormStaff } from './weapon.js';
import { Player, Warrior, Archer, Mage, Dwarf, Crossbowman, Demiurge } from './player.js';
import { play } from './game.js';

function assertEqual(actual, expected, message) {
  if (Math.abs(actual - expected) > 0.001) {
    throw new Error(`${message}: ожидалось ${expected}, получено ${actual}`);
  }
}

function assertTrue(value, message) {
  if (!value) {
    throw new Error(message);
  }
}

console.log('Тестирование оружия...');

let arm = new Arm();
assertEqual(arm.durability, Infinity, 'Arm durability');
arm.takeDamage(20);
assertEqual(arm.durability, Infinity, 'Arm durability after damage');

let sword = new Sword();
assertEqual(sword.durability, 500, 'Sword durability');
sword.takeDamage(20);
assertEqual(sword.durability, 480, 'Sword durability after 20 damage');
sword.takeDamage(100);
assertEqual(sword.durability, 380, 'Sword durability after 100 damage');

let bow = new Bow();
assertEqual(bow.getDamage(), 10, 'Bow damage at full durability');
bow.takeDamage(100);
assertEqual(bow.getDamage(), 10, 'Bow damage at 100 durability');
bow.takeDamage(50);
assertEqual(bow.getDamage(), 5, 'Bow damage at 50 durability (below 30%)');
bow.takeDamage(150);
assertEqual(bow.getDamage(), 0, 'Bow damage at 0 durability');
assertTrue(bow.isBroken(), 'Bow should be broken');

let longBow = new LongBow();
assertEqual(longBow.attack, 15, 'LongBow attack');
assertEqual(longBow.range, 4, 'LongBow range');

let axe = new Axe();
assertEqual(axe.attack, 27, 'Axe attack');
assertEqual(axe.durability, 800, 'Axe durability');

let stormStaff = new StormStaff();
assertEqual(stormStaff.attack, 10, 'StormStaff attack');
assertEqual(stormStaff.range, 3, 'StormStaff range');

console.log('Тестирование персонажей...');

let player = new Player(10, 'Тест');
assertEqual(player.life, 100, 'Player life');
assertEqual(player.magic, 20, 'Player magic');

player.takeDamage(10);
assertEqual(player.life, 90, 'Player life after 10 damage');
player.takeDamage(80);
assertEqual(player.life, 10, 'Player life after 80 damage');
player.takeDamage(90);
assertEqual(player.life, 0, 'Player life after 90 damage');
assertTrue(player.isDead(), 'Player should be dead');

let warrior = new Warrior(0, 'Воин');
assertEqual(warrior.life, 120, 'Warrior life');
assertEqual(warrior.speed, 2, 'Warrior speed');

warrior.takeDamage(50);
assertEqual(warrior.life, 70, 'Warrior life after 50 damage');
warrior.takeDamage(20);
assertEqual(warrior.life, 50, 'Warrior life after 20 damage');

let archer = new Archer(5, 'Лучник');
assertEqual(archer.life, 80, 'Archer life');
assertEqual(archer.agility, 10, 'Archer agility');

let mage = new Mage(10, 'Маг');
assertEqual(mage.life, 70, 'Mage life');
assertEqual(mage.magic, 100, 'Mage magic');

mage.takeDamage(50);
assertEqual(mage.life, 45, 'Mage life after 50 damage (reduced by shield)');
assertEqual(mage.magic, 88, 'Mage magic after using shield');

let dwarf = new Dwarf(15, 'Гном');
assertEqual(dwarf.life, 130, 'Dwarf life');

let crossbowman = new Crossbowman(20, 'Арбалетчик');
assertEqual(crossbowman.life, 85, 'Crossbowman life');

let demiurge = new Demiurge(25, 'Демиург');
assertEqual(demiurge.life, 80, 'Demiurge life');
assertEqual(demiurge.magic, 120, 'Demiurge magic');

console.log('Тестирование движения...');
let testPlayer = new Warrior(6, 'Тест');
testPlayer.moveLeft(5);
assertEqual(testPlayer.position, 4, 'Position after moveLeft(5) with speed 2');
testPlayer.moveRight(2);
assertEqual(testPlayer.position, 6, 'Position after moveRight(2)');

console.log('Тестирование боя...');
let p1 = new Warrior(0, 'Воин1');
let p2 = new Archer(1, 'Лучник1');

const initialLife = p2.life;
p1.tryAttack(p2);
assertTrue(p2.life <= initialLife, 'Archer life should not increase');

console.log('Все тесты пройдены!');
