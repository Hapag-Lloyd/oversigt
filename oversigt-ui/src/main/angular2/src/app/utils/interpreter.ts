
/* self-interpretation */
export function interpret(object: any, instructions: string, index: number): string {
  const regex = new RegExp('\{\{([^}]+)}}');
  const array = instructions.split(regex); // regex.exec(instructions);

  if (instructions !== undefined) {
    for (let i = 1; i < array.length; i += 2) {
      array[i] = computeReplacement(object, array[i], index);
    }
  }

  return array.join('');
}

function computeReplacement(object: any, instruction: string, index: number): string {
  instruction = instruction.trim();
  if (instruction === 'i0') {
    return String(index);
  } else if (instruction === 'i1') {
    return String(index + 1);
  } else {
    const regex = new RegExp('(?:self|item)\s*\.(.+)');
    if (regex.test(instruction)) {
      const members = regex.exec(instruction);
      return object[members[1].trim()];
    } else {
      throw new Error('Unable to interpret instruction: ' + instruction);
    }
  }
}
