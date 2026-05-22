import { Player, Warrior, Archer, Mage, Dwarf, Crossbowman, Demiurge } from './player.js';

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function play(players, onUpdate = null, delay = 500) {
  let round = 1;
  
  while (true) {
    const alivePlayers = players.filter(p => !p.isDead());
    
    if (alivePlayers.length <= 1) {
      const winner = alivePlayers[0];
      console.log(`Игра окончена! Победитель: ${winner ? winner.name : 'Ничья'}`);
      if (onUpdate) onUpdate(players, null, 'end');
      return winner;
    }
    
    console.log(`\n--- Раунд ${round} ---`);
    if (onUpdate) onUpdate(players, null, 'round', round);
    
    for (const player of players) {
      if (player.isDead()) continue;
      
      player.turn(players);
      if (onUpdate) onUpdate(players, player, 'turn');
      
      await sleep(delay);
      
      const aliveAfterTurn = players.filter(p => !p.isDead());
      if (aliveAfterTurn.length <= 1) {
        const winner = aliveAfterTurn[0];
        console.log(`Игра окончена! Победитель: ${winner ? winner.name : 'Ничья'}`);
        if (onUpdate) onUpdate(players, winner, 'end');
        return winner;
      }
    }
    
    round++;
  }
}

export { play, Player, Warrior, Archer, Mage, Dwarf, Crossbowman, Demiurge };
