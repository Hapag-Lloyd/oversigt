
/* self-interpretation */
export function interpret(object: any, instructions: string, defaultValue: string = ''): string {
  const instructionsWithoutBraces = instructions.substring(2, instructions.length - 2).trim();

  let output;
  if (instructions !== undefined) {
    if (instructionsWithoutBraces.startsWith('self.')) {
      const element = instructionsWithoutBraces.substring('self.'.length);
      if (element.includes('.')) {
        throw new Error('Unable to interpret: ' + instructions);
      }
      output = object[element];
    } else if (instructionsWithoutBraces.startsWith('item.')) {
      const element = instructionsWithoutBraces.substring('item.'.length);
      if (element.includes('.')) {
        throw new Error('Unable to interpret: ' + instructions);
      }
      output = object[element];
    } else {
      throw new Error('Unrecognized instructions: ' + instructions);
    }
  }

  if (output !== undefined && output !== null && output !== '') {
    return output;
  } else {
    return defaultValue;
  }
}
