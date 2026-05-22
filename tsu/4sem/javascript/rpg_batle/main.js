import { play, Warrior, Archer, Mage, Dwarf, Crossbowman, Demiurge } from './game.js';

const battleField = document.getElementById('battle-field');
const logsContainer = document.getElementById('logs');
const startBtn = document.getElementById('start-btn');

function getMaxLife(player) {
  if (player instanceof Dwarf) return 130;
  if (player instanceof Crossbowman) return 85;
  if (player instanceof Demiurge) return 80;
  if (player instanceof Warrior) return 120;
  if (player instanceof Archer) return 80;
  if (player instanceof Mage) return 70;
  return 100;
}

function createPlayerCard(player) {
  const card = document.createElement('div');
  card.className = 'player-card';
  card.id = `player-${player.name}`;
  
  const maxLife = getMaxLife(player);
  const healthPercent = (player.life / maxLife) * 100;
  
  card.innerHTML = `
    <div class="player-name">${player.name} (${player.description})</div>
    <div class="player-stats">
      <div class="stat"><span class="stat-label">Жизнь:</span> <span class="stat-value" id="life-${player.name}">${Math.round(player.life)}</span></div>
      <div class="stat"><span class="stat-label">Мана:</span> <span class="stat-value" id="magic-${player.name}">${Math.round(player.magic)}</span></div>
      <div class="stat"><span class="stat-label">Позиция:</span> <span class="stat-value" id="pos-${player.name}">${player.position}</span></div>
      <div class="stat"><span class="stat-label">Оружие:</span> <span class="stat-value" id="weapon-${player.name}">${player.weapon.name}</span></div>
    </div>
    <div class="health-bar"><div class="health-fill" id="health-${player.name}" style="width: ${healthPercent}%"></div></div>
  `;
  
  return card;
}

function updatePlayerCard(player) {
  const lifeEl = document.getElementById(`life-${player.name}`);
  const magicEl = document.getElementById(`magic-${player.name}`);
  const posEl = document.getElementById(`pos-${player.name}`);
  const weaponEl = document.getElementById(`weapon-${player.name}`);
  const healthBar = document.getElementById(`health-${player.name}`);
  const card = document.getElementById(`player-${player.name}`);
  
  if (lifeEl) lifeEl.textContent = Math.round(player.life);
  if (magicEl) magicEl.textContent = Math.round(player.magic);
  if (posEl) posEl.textContent = Math.round(player.position);
  if (weaponEl) weaponEl.textContent = player.weapon.name;
  
  const maxLife = getMaxLife(player);
  
  if (healthBar) {
    healthBar.style.width = `${(player.life / maxLife) * 100}%`;
  }
  
  if (card && player.isDead()) {
    card.classList.add('dead');
  }
}

function addLog(message, type = 'info') {
  const entry = document.createElement('div');
  entry.className = `log-entry ${type}`;
  entry.textContent = message;
  logsContainer.appendChild(entry);
  logsContainer.scrollTop = logsContainer.scrollHeight;
}

function initBattleField(players) {
  battleField.innerHTML = '';
  players.forEach(player => {
    battleField.appendChild(createPlayerCard(player));
  });
}

function updateBattleField(players) {
  players.forEach(player => updatePlayerCard(player));
}

async function runBattle() {
  startBtn.disabled = true;
  startBtn.textContent = 'Битва идет...';
  logsContainer.innerHTML = '';
  
  const players = [
    new Warrior(0, 'Алёша Попович'),
    new Archer(5, 'Леголас'),
    new Mage(10, 'Гендальф'),
    new Dwarf(15, 'Гимли'),
    new Crossbowman(20, 'Робин'),
    new Demiurge(25, 'Зевс')
  ];
  
  initBattleField(players);
  
  const originalLog = console.log;
  console.log = (...args) => {
    const message = args.join(' ');
    let type = 'info';
    if (message.includes('атакует') || message.includes('урона')) type = 'attack';
    if (message.includes('двигается') || message.includes('Позиция')) type = 'move';
    addLog(message, type);
    originalLog.apply(console, args);
  };
  
  const onUpdate = (players, currentPlayer, eventType, round) => {
    updateBattleField(players);
    if (eventType === 'round') {
      addLog(`\n=== Раунд ${round} ===`, 'info');
    }
  };
  
  const winner = await play(players, onUpdate, 300);
  
  console.log = originalLog;
  
  updateBattleField(players);
  
  if (winner) {
    const winnerCard = document.getElementById(`player-${winner.name}`);
    if (winnerCard) winnerCard.classList.add('winner');
    addLog(`\nПобедитель: ${winner.name}!`, 'info');
  }
  
  startBtn.disabled = false;
  startBtn.textContent = 'Начать новую битву!';
}

startBtn.addEventListener('click', runBattle);
