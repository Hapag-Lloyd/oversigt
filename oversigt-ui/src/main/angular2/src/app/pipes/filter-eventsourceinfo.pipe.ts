import { Pipe, PipeTransform } from '@angular/core';
import { EventSourceInfo } from 'src/oversigt-client';

@Pipe({
  name: 'filterEventsourceinfo'
})
export class FilterEventsourceinfoPipe implements PipeTransform {

  transform(items: EventSourceInfo[], searchText: string): any[] {
    if (!items) {
      return [];
    }
    if (!searchText) {
      return items;
    }

    searchText = searchText.toLowerCase();

    return items.filter( it => {
      return it.name.toLowerCase().includes(searchText) || (it.description && it.description.toLowerCase().includes(searchText));
    });
  }
}
