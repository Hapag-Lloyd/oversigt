import { ParsedEventSourceInstanceDetails } from '../config/eventsources-details/config-eventsources-details.component';

function onlyUnique(value, index, self) {
  return self.indexOf(value) === index;
}

export function uniqueItems<T>(array: ParsedEventSourceInstanceDetails[]): ParsedEventSourceInstanceDetails[] {
  const ids = array.map(p => p.id);
  const uniqueIds = ids.filter(onlyUnique);
  return uniqueIds.map(id => array.find(p => p.id === id));
}
