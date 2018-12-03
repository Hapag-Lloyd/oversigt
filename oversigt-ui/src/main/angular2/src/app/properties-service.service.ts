import { Injectable } from '@angular/core';
import { SerializableValueService } from 'src/oversigt-client';

@Injectable({
  providedIn: 'root'
})
export class PropertiesService {
  private properties: string[] = undefined;

  constructor(
    private propertiesService: SerializableValueService
  ) { }

  loadProperties(callback: ([]) => void): void {
    if (this.properties === undefined) {
      this.properties = null;
      this.propertiesService.listPropertyTypes().subscribe(
        list => {
          this.properties = list;
          callback(list);
        },
        error => {
          console.error(error);
          alert(error);
          // TODO: error handling
        }
      );
    } else if (this.properties === null) {
      setTimeout(() => {
        this.loadProperties(callback);
      }, 250);
    } else {
      callback(this.properties);
    }
  }
}
