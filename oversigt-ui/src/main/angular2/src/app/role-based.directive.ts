import { Directive, Input, TemplateRef, ViewContainerRef } from '@angular/core';
import { UserService } from './user-service.service';

@Directive({
  selector: '[appHasRole]'
})
export class HasRoleDirective {
  private hasRole = false;

  @Input() set appHasRole(role: string) {
    const hasRole = this.userService.hasRole(role);
    if (hasRole && !this.hasRole) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasRole = true;
    } else if (!hasRole && this.hasRole) {
      this.viewContainer.clear();
      this.hasRole = false;
    }
  }

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef,
    private userService: UserService,
  ) { }

}
