import { Injectable } from '@angular/core';
import { SerializableValueService, SerializablePropertyDescription } from 'src/oversigt-client';
import { ErrorHandlerService } from './error-handler.service';

@Injectable({
  providedIn: 'root'
})
export class PropertiesService {
  private properties: SerializablePropertyDescription[] = undefined;

  constructor(
    private propertiesService: SerializableValueService,
    private errorHandler: ErrorHandlerService,
  ) { }

  loadProperties(callback: ([]) => void): void {
    if (this.properties === undefined) {
      this.properties = null;
      this.propertiesService.listPropertyTypes().subscribe(
        list => {
          this.properties = list.sort((a, b) => a.name > b.name ? 1 : -1);
          callback(list);
        },
        this.errorHandler.createErrorHandler('Listing property types'));
    } else if (this.properties === null) {
      setTimeout(() => {
        this.loadProperties(callback);
      }, 250);
    } else {
      callback(this.properties);
    }
  }
}
