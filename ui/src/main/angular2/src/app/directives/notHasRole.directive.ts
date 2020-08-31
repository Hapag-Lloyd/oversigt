import { Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';
import { UserService } from '../services/user-service.service';

@Directive({
  selector: '[appNotHasRole]'
})
export class NotHasRoleDirective {
  private hasRole = false;
  @Input()
  set appNotHasRole(role: string) {
    const hasRole = !(role === undefined
                  || role === null
                  || String(role).trim().length === 0
                  || this.userService.hasRole(role));
    if (hasRole && !this.hasRole) {
      this.viewContainer.createEmbeddedView(this.templateRef);
    } else if (!hasRole && this.hasRole) {
      this.viewContainer.clear();
    }
    this.hasRole = hasRole;
  }
  constructor(private templateRef: TemplateRef<any>, private viewContainer: ViewContainerRef, private userService: UserService) { }
}
