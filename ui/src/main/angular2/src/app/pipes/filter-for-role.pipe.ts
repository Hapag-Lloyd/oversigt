import { Pipe, PipeTransform } from '@angular/core';
import { UserService } from '../services/user-service.service';

@Pipe({
  name: 'filterForRole'
})
export class FilterForRolePipe implements PipeTransform {

  constructor(
    private userService: UserService,
  ) { }

  transform(items: any[]): any[] {
    if (!items) {
      return [];
    }

    return items.filter(item => this.isItemAllowed(item));
  }

  private isItemAllowed(item: any): boolean {
    const requiredRole: string = item.requiredRole;
    if (requiredRole === undefined || requiredRole === null) {
      return true;
    }

    return this.userService.hasRole(requiredRole);
  }
}
