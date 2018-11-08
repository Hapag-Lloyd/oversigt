import { Pipe, PipeTransform } from '@angular/core';
import { EventItem } from '../config/events/config-events.component';

@Pipe({
  name: 'filterEventitem'
})
export class FilterEventitemPipe implements PipeTransform {

  transform(items: EventItem[], searchText: string): any[] {
    if (!items) {
      return [];
    }
    if (!searchText) {
      return items;
    }

    searchText = searchText.toLowerCase();

    return items.filter( it => {
      if (it['event'] !== undefined) {
        return JSON.stringify(it.event).toLowerCase().includes(searchText);
      } else {
        return JSON.stringify(it).toLowerCase().includes(searchText);
      }
    });
  }
}
